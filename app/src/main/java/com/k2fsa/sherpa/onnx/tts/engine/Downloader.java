package com.k2fsa.sherpa.onnx.tts.engine;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import io.orabel.orabelandroid.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import io.orabel.orabelandroid.databinding.ActivityManageLanguagesBinding;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
	static final String onnxModel = "model.onnx";
	static final String tokens = "tokens.txt";
	static long onnxModelDownloadSize = 0L;
	static long tokensDownloadSize = 0L;
	static boolean onnxModelFinished = false;
	static boolean tokensFinished = false;
	static int onnxModelSize = 0;
	static int tokensSize = 0;

	   public static void downloadModels(final Activity activity, ActivityManageLanguagesBinding binding, String model, String lang, String country, String type) {
		   String modelName = "";
		   if (type.equals("vits-piper")) modelName = model + ".onnx";
		   else if (type.equals("vits-coqui")) modelName = "model.onnx";

		   String onnxModelUrl = "https://huggingface.co/csukuangfj/" + type + "-" + model + "/resolve/main/" + modelName;
		   String tokensUrl = "https://huggingface.co/csukuangfj/" + type + "-" + model + "/resolve/main/tokens.txt";

		   File directory = new File(activity.getExternalFilesDir(null) + "/" + lang + country + "/");
		   if (!directory.exists() && !directory.mkdirs()) {
			   Log.e("TTS Engine", "Failed to make directory: " + directory);
			   return;
		   }

		   activity.runOnUiThread(() -> {
			   binding.downloadSize.setVisibility(View.VISIBLE);
			   if (binding.downloadProgress != null) {
				   binding.downloadProgress.setVisibility(View.VISIBLE);
				   binding.downloadProgress.setProgress(0);
			   }
			   binding.downloadSize.setText(activity.getString(R.string.downloading_model));
		   });

		   File onnxModelFile = new File(activity.getExternalFilesDir(null) + "/" + lang + country + "/" + onnxModel);
		   if (onnxModelFile.exists()) onnxModelFile.delete();
		   if (!onnxModelFile.exists()) {
			   onnxModelFinished = false;
			   Log.d("TTS Engine", "onnx model file does not exist");
			   Thread thread = new Thread(() -> {
				   try {
					   URL url = new URL(onnxModelUrl);
					   Log.d("TTS Engine", "Download model");
					   URLConnection ucon = url.openConnection();
					   ucon.setReadTimeout(5000);
					   ucon.setConnectTimeout(10000);
					   onnxModelSize = ucon.getContentLength();

					   InputStream is = ucon.getInputStream();
					   BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

					   File tempOnnxFile = new File(activity.getExternalFilesDir(null) + "/" + lang + country + "/model.tmp");
					   if (tempOnnxFile.exists()) tempOnnxFile.delete();

					   FileOutputStream outStream = new FileOutputStream(tempOnnxFile);
					   byte[] buff = new byte[5 * 1024];

					   int len;
					   while ((len = inStream.read(buff)) != -1) {
						   outStream.write(buff, 0, len);
						   if (tempOnnxFile.exists()) onnxModelDownloadSize = tempOnnxFile.length();
						  activity.runOnUiThread(() -> {
							   long done = (tokensDownloadSize + onnxModelDownloadSize);
							   long total = (onnxModelSize + tokensSize);
							   int progress = (total > 0) ? (int) (100 * done / total) : 0;
							   if (binding.downloadProgress != null) binding.downloadProgress.setProgress(progress);
							   binding.downloadSize.setText((done / 1024 / 1024) + " MB / " + (total / 1024 / 1024) + " MB");
						   });
					   }
					   outStream.flush();
					   outStream.close();
					   inStream.close();

					   if (!tempOnnxFile.exists()) {
						   throw new IOException();
					   }

					   tempOnnxFile.renameTo(onnxModelFile);
					   onnxModelFinished = true;
					   activity.runOnUiThread(() -> {
						   if (tokensFinished && onnxModelFinished && binding.buttonStart.getVisibility() == View.GONE) {
							   binding.buttonStart.setVisibility(View.VISIBLE);
							   if (binding.downloadProgress != null) binding.downloadProgress.setVisibility(View.GONE);
							   binding.downloadSize.setText(activity.getString(R.string.model_ready_tts));
							   PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
							   preferenceHelper.setCurrentLanguage(lang);
							   LangDB langDB = LangDB.getInstance(activity);
							   langDB.addLanguage(model, lang, country, 0, 1.0f, type);
						   }
					   });
				   } catch (IOException e) {
					   activity.runOnUiThread(() -> Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show());
				   }
			   });
			   thread.start();
		   }

		   // Descargar tokens.txt igual que en ttsEngine-master
		   File tokensFile = new File(activity.getExternalFilesDir(null) + "/" + lang + country + "/" + tokens);
		   if (tokensFile.exists()) tokensFile.delete();
		   if (!tokensFile.exists()) {
			   tokensFinished = false;
			   Log.d("TTS Engine", "tokens file does not exist");
			   Thread thread = new Thread(() -> {
				   try {
					   URL url = new URL(tokensUrl);
					   Log.d("TTS Engine", "Download tokens file");

					   URLConnection ucon = url.openConnection();
					   ucon.setReadTimeout(5000);
					   ucon.setConnectTimeout(10000);
					   tokensSize = ucon.getContentLength();

					   InputStream is = ucon.getInputStream();
					   BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

					   File tempTokensFile = new File(activity.getExternalFilesDir(null) + "/" + lang + country + "/tokens.tmp");
					   if (tempTokensFile.exists()) tempTokensFile.delete();

					   FileOutputStream outStream = new FileOutputStream(tempTokensFile);
					   byte[] buff = new byte[5 * 1024];

					   int len;
					   while ((len = inStream.read(buff)) != -1) {
						   outStream.write(buff, 0, len);
						   if (tempTokensFile.exists()) tokensDownloadSize = tempTokensFile.length();
						  activity.runOnUiThread(() -> {
							   long done = (tokensDownloadSize + onnxModelDownloadSize);
							   long total = (onnxModelSize + tokensSize);
							   int progress = (total > 0) ? (int) (100 * done / total) : 0;
							   if (binding.downloadProgress != null) binding.downloadProgress.setProgress(progress);
							   binding.downloadSize.setText((done / 1024 / 1024) + " MB / " + (total / 1024 / 1024) + " MB");
						   });
					   }
					   outStream.flush();
					   outStream.close();
					   inStream.close();

					   if (!tempTokensFile.exists()) {
						   throw new IOException();
					   }

					   tempTokensFile.renameTo(tokensFile);
					   tokensFinished = true;
					   activity.runOnUiThread(() -> {
						   if (tokensFinished && onnxModelFinished && binding.buttonStart.getVisibility() == View.GONE) {
							   binding.buttonStart.setVisibility(View.VISIBLE);
							   if (binding.downloadProgress != null) binding.downloadProgress.setVisibility(View.GONE);
							   binding.downloadSize.setText(activity.getString(R.string.model_ready_tts));
							   PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
							   preferenceHelper.setCurrentLanguage(lang);
							   LangDB langDB = LangDB.getInstance(activity);
							   langDB.addLanguage(model, lang, country, 0, 1.0f, type);
						   }
					   });
				   } catch (IOException i) {
					   activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
					   tokensFile.delete();
					   Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
				   }
			   });
			   thread.start();
		   }
	   }
}
