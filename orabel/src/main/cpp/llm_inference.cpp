#include "llm_inference.h"
#include "common.h"
#include <cstring>
#include <iostream>
#include <vector>
#include <string>
#include <android/log.h>

#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)


void LLMInference::load_model(const char *model_path, float min_p, float temperature, bool store_chats) {
    // Initialize llama backend (this is critical and often forgotten)
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
        LOGi("llama backend initialized");
    }

    // Validate input parameters
    if (!model_path || strlen(model_path) == 0) {
        LOGe("Invalid model path provided");
        throw std::runtime_error("load_model() failed: invalid model path");
    }

    if (min_p < 0.0f || min_p > 1.0f) {
        LOGe("Invalid min_p value: %f. Must be between 0.0 and 1.0", min_p);
        throw std::runtime_error("load_model() failed: invalid min_p value");
    }

    if (temperature < 0.0f || temperature > 10.0f) {
        LOGe("Invalid temperature value: %f. Must be between 0.0 and 10.0", temperature);
        throw std::runtime_error("load_model() failed: invalid temperature value");
    }

    LOGi("Loading model from: %s", model_path);
    LOGi("Parameters: min_p=%.2f, temperature=%.2f, store_chats=%s", min_p, temperature, store_chats ? "true" : "false");

    // create an instance of llama_model
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;     // Use memory mapping for better performance
    model_params.use_mlock = false;   // Don't lock memory (problematic on mobile)

    model = llama_model_load_from_file(model_path, model_params);

    if (!model) {
        LOGe("failed to load model from %s", model_path);
        throw std::runtime_error("load_model() failed: could not load model file");
    }

    // Get model info for validation
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGe("Failed to get model vocabulary");
        llama_model_free(model);
        model = nullptr;
        throw std::runtime_error("Failed to get model vocabulary");
    }
    int vocab_size = llama_vocab_n_tokens(vocab);
    int ctx_size = llama_model_n_ctx_train(model);
    LOGi("Model loaded successfully. Vocab size: %d, Training context: %d", vocab_size, ctx_size);

    // create an instance of llama_context
    llama_context_params ctx_params = llama_context_default_params();

    // Use smaller context size for mobile to prevent memory issues
    ctx_params.n_ctx = std::min(2048, ctx_size);  // Cap at 2048 or model's training context
    ctx_params.no_perf = true;          // disable performance metrics
    ctx_params.flash_attn = false;      // Disable flash attention on mobile

    LOGi("Creating context with size: %d", ctx_params.n_ctx);

    ctx = llama_init_from_model(model, ctx_params);

    if (!ctx) {
        LOGe("llama_init_from_model() returned null");
        llama_model_free(model);
        model = nullptr;
        throw std::runtime_error("llama_init_from_model() returned null");
    }

    // initialize sampler with validation
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;      // disable performance metrics
    sampler = llama_sampler_chain_init(sampler_params);

    if (!sampler) {
        LOGe("Failed to initialize sampler");
        llama_free(ctx);
        llama_model_free(model);
        ctx = nullptr;
        model = nullptr;
        throw std::runtime_error("Failed to initialize sampler");
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    formatted = std::vector<char>(llama_n_ctx(ctx) * 4); // Allocate more space for safety
    messages.clear();
    this->store_chats = store_chats;

    LOGi("Model initialization completed successfully");
}

void LLMInference::add_chat_message(const char *message, const char *role) {
    // Validate input parameters
    if (!message || !role) {
        LOGe("Invalid message or role parameter in add_chat_message");
        return;
    }

    if (strlen(message) == 0 || strlen(role) == 0) {
        LOGe("Empty message or role string in add_chat_message");
        return;
    }

    // Limit message length to prevent memory issues
    const size_t MAX_MESSAGE_LENGTH = 8192;
    if (strlen(message) > MAX_MESSAGE_LENGTH) {
        LOGe("Message too long (%zu chars), truncating to %zu", strlen(message), MAX_MESSAGE_LENGTH);
        char* truncated_message = static_cast<char*>(malloc(MAX_MESSAGE_LENGTH + 1));
        if (!truncated_message) {
            LOGe("Failed to allocate memory for truncated message");
            return;
        }
        strncpy(truncated_message, message, MAX_MESSAGE_LENGTH);
        truncated_message[MAX_MESSAGE_LENGTH] = '\0';
        messages.push_back({strdup(role), truncated_message});
    } else {
        messages.push_back({strdup(role), strdup(message)});
    }
}

void LLMInference::start_completion(const char *query) {
    // Validate input
    if (!query || strlen(query) == 0) {
        LOGe("Invalid or empty query in start_completion");
        throw std::runtime_error("start_completion() failed: invalid query");
    }

    // Validate model and context state
    if (!model || !ctx || !sampler) {
        LOGe("Model, context, or sampler not initialized");
        throw std::runtime_error("start_completion() failed: model not properly initialized");
    }

    if (!store_chats) {
        prev_len = 0;
        formatted.clear();
    }
    add_chat_message(query, "user");

    // Get context size and check if we need to truncate messages
    int context_size = llama_n_ctx(ctx);
    int max_context_tokens = context_size - 800; // Increased reserve for longer responses

    // Get chat template from model
    const char* chat_template = llama_model_chat_template(model, nullptr);
    if (!chat_template) {
        LOGe("Failed to get chat template from model");
        throw std::runtime_error("start_completion() failed: no chat template available");
    }

    // Try to apply template with all messages first
    int new_len = llama_chat_apply_template(
            chat_template,  // Use model's chat template
            messages.data(),
            messages.size(),
            true,
            formatted.data(),
            formatted.size()
    );

    if (new_len > (int)formatted.size()) {
        formatted.resize(new_len);
        new_len = llama_chat_apply_template(chat_template, messages.data(), messages.size(), true, formatted.data(), formatted.size());
    }

    if (new_len < 0) {
        throw std::runtime_error("llama_chat_apply_template() in LLMInference::start_completion() failed");
    }

    // Count tokens in the prompt
    std::string prompt(formatted.begin() + prev_len, formatted.begin() + new_len);
    std::vector<llama_token> prompt_tokens = common_tokenize(ctx, prompt, true, true);

    // If prompt is too long, truncate old messages (keep system message and recent messages)
    if ((int)prompt_tokens.size() > max_context_tokens) {
        LOGi("Context too long (%zu tokens), truncating old messages", prompt_tokens.size());

        // Keep system message (index 0) and last few user/assistant pairs
        size_t messages_to_keep = 5; // System + last 2 user/assistant pairs
        if (messages.size() > messages_to_keep) {
            // Remove old messages (but keep system message if it exists)
            size_t start_remove = 1; // Start after system message
            size_t end_remove = messages.size() - messages_to_keep + 1;

            for (size_t i = start_remove; i < end_remove && i < messages.size(); ++i) {
                free(const_cast<void*>(static_cast<const void*>(messages[i].content)));
                free(const_cast<void*>(static_cast<const void*>(messages[i].role)));
            }

            messages.erase(messages.begin() + start_remove, messages.begin() + end_remove);
        }

        // Recalculate with truncated messages
        new_len = llama_chat_apply_template(
                chat_template,  // Use chat template
                messages.data(),
                messages.size(),
                true,
                formatted.data(),
                formatted.size()
        );

        if (new_len < 0) {
            throw std::runtime_error("llama_chat_apply_template() after truncation failed");
        }

        prev_len = 0; // Reset previous length after truncation
    }

    std::string final_prompt(formatted.begin() + prev_len, formatted.begin() + new_len);
    std::vector<llama_token> final_tokens = common_tokenize(ctx, final_prompt, true, true);

    LOGi("Final prompt tokens: %zu", final_tokens.size());

    // create a llama_batch containing a single sequence
    batch = llama_batch_get_one(final_tokens.data(), final_tokens.size());
}


std::string LLMInference::completion_loop() {
    // Validate state before proceeding
    if (!ctx || !model || !sampler) {
        LOGe("Invalid state: ctx=%p, model=%p, sampler=%p", ctx, model, sampler);
        return "[INVALID_STATE]";
    }

    // check if the length of the inputs to the model
    // have exceeded the context size of the model
    int context_size = llama_n_ctx(ctx);

    // Use more conservative buffer for longer mathematical responses
    if (batch.n_tokens > context_size - 300) { // Increased buffer for longer responses
        LOGe("Context size exceeded: %d tokens, max: %d", batch.n_tokens, context_size);
        return "[CONTEXT_EXCEEDED]";
    }

    // Additional safety check for batch validity
    if (!batch.token || batch.n_tokens <= 0) {
        LOGe("Invalid batch: tokens=%p, n_tokens=%d", batch.token, batch.n_tokens);
        return "[INVALID_BATCH]";
    }

    // run the model with error checking
    int decode_result = llama_decode(ctx, batch);
    if (decode_result < 0) {
        LOGe("llama_decode() failed with code: %d", decode_result);
        return "[DECODE_ERROR]";
    }

    // sample a token and check if it is an EOG (end of generation token)
    // convert the integer token to its correspond word-piece

    // Ensure we have valid logits before sampling
    float * logits = llama_get_logits_ith(ctx, -1);
    if (!logits) {
        LOGe("Failed to get logits from context");
        return "[LOGITS_ERROR]";
    }

    curr_token = llama_sampler_sample(sampler, ctx, -1);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // Additional safety check for vocab
    if (!vocab) {
        LOGe("Failed to get model vocabulary");
        return "[VOCAB_ERROR]";
    }

    if (llama_vocab_is_eog(vocab, curr_token)) { // Use correct modern API
        return "[EOG]";
    }

    // Safety check for token validity
    if (curr_token < 0) {
        LOGe("Invalid token sampled: %d", curr_token);
        return "[INVALID_TOKEN]";
    }

    // Check if token is within vocabulary range
    int vocab_size = llama_vocab_n_tokens(vocab);
    if (curr_token >= vocab_size) {
        LOGe("Token %d exceeds vocabulary size %d", curr_token, vocab_size);
        return "[TOKEN_OUT_OF_RANGE]";
    }

    std::string piece = common_token_to_piece(ctx, curr_token, true);

    // Filter out chat format tokens that shouldn't be shown to user
    if (piece == "<|im_end|>" || piece == "<|im_start|>assistant" ||
        piece == "<|im_start|>" || piece == "assistant" ||
        piece.find("<|im_") != std::string::npos) {
        // Skip these tokens but continue generation
        batch = llama_batch_get_one(&curr_token, 1);
        return ""; // Return empty string instead of the format token
    }

    response += piece;

    LOGi("Generated piece: %s", piece.c_str());

    // re-init the batch with the newly predicted token
    // key, value pairs of all previous tokens have been cached
    // in the KV cache
    batch = llama_batch_get_one(&curr_token, 1);
    return piece;
}


void LLMInference::stop_completion() {
    // Validate state
    if (!model || !ctx) {
        LOGe("Invalid state in stop_completion: model=%p, ctx=%p", model, ctx);
        return;
    }

    // Clean up any remaining format tokens from the response
    std::string cleaned_response = response;
    size_t pos = 0;

    // Remove common chat format tokens
    std::vector<std::string> tokens_to_remove = {
        "<|im_end|>", "<|im_start|>assistant", "<|im_start|>",
        "<|end|>", "<|assistant|>", "<|user|>", "<|system|>"
    };

    for (const std::string& token : tokens_to_remove) {
        while ((pos = cleaned_response.find(token)) != std::string::npos) {
            cleaned_response.erase(pos, token.length());
        }
    }

    // Trim whitespace from beginning and end
    cleaned_response.erase(0, cleaned_response.find_first_not_of(" \t\n\r"));
    cleaned_response.erase(cleaned_response.find_last_not_of(" \t\n\r") + 1);

    if (store_chats && !cleaned_response.empty()) {
        // Use const char* directly instead of strdup since we're not storing it long-term
        add_chat_message(cleaned_response.c_str(), "assistant");
    }
    response.clear();

    // Safely calculate previous length
    try {
        const char* chat_template = llama_model_chat_template(model, nullptr);
        if (chat_template) {
            prev_len = llama_chat_apply_template(
                    chat_template,  // Use chat template
                    messages.data(),
                    messages.size(),
                    false,
                    nullptr,
                    0
            );
            if (prev_len < 0) {
                LOGe("llama_chat_apply_template() returned negative length: %d", prev_len);
                prev_len = 0;  // Reset to safe value
            }
        } else {
            LOGe("No chat template available in stop_completion");
            prev_len = 0;
        }
    } catch (const std::exception& e) {
        LOGe("Exception in stop_completion: %s", e.what());
        prev_len = 0;
    }
}

void LLMInference::cancel_completion() {
    // Validate state
    if (!model || !ctx) {
        LOGe("Invalid state in cancel_completion: model=%p, ctx=%p", model, ctx);
        return;
    }

    LOGi("Cancelling completion, discarding partial response");

    // Simply clear the response without saving it
    response.clear();

    // Reset the previous length calculation without saving the assistant message
    try {
        const char* chat_template = llama_model_chat_template(model, nullptr);
        if (chat_template) {
            prev_len = llama_chat_apply_template(
                    chat_template,  // Use chat template
                    messages.data(),
                    messages.size(),
                    false,
                    nullptr,
                    0
            );
            if (prev_len < 0) {
                LOGe("llama_chat_apply_template() returned negative length: %d", prev_len);
                prev_len = 0;  // Reset to safe value
            }
        } else {
            LOGe("No chat template available in cancel_completion");
            prev_len = 0;
        }
    } catch (const std::exception& e) {
        LOGe("Exception in cancel_completion: %s", e.what());
        prev_len = 0;
    }
}


LLMInference::~LLMInference() {
    LOGi("Starting cleanup of LLMInference");

    // free memory held by the message text in messages
    // (as we had used strdup() to create a malloc'ed copy)
    for (llama_chat_message &message: messages) {
        if (message.content) {
            free(const_cast<void*>(static_cast<const void*>(message.content)));
            message.content = nullptr;
        }
        if (message.role) {
            free(const_cast<void*>(static_cast<const void*>(message.role)));
            message.role = nullptr;
        }
    }
    messages.clear();

    // Clean up sampler first
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
        LOGi("Sampler freed");
    }

    // Clean up context
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
        LOGi("Context freed");
    }

    // Clean up model last
    if (model) {
        llama_model_free(model);
        model = nullptr;
        LOGi("Model freed");
    }

    // Clear other resources
    formatted.clear();
    response.clear();

    // Note: We don't call llama_backend_free() here because it's global
    // and may be used by other instances

    LOGi("LLMInference cleanup completed");
}