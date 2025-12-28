import Foundation
import SwiftData

/// Local storage model for read messages (deleted from server)
@Model
final class LocalMessage {
    @Attribute(.unique) var id: Int64
    var senderId: UUID
    var receiverId: UUID
    var content: String
    var createdAt: Date
    var readAt: Date
    
    // Sender info (cached)
    var senderUsername: String?
    var senderAvatarUrl: String?
    
    // Reply/Thread context (for maintaining reply UI even after server deletion)
    var replyToId: Int64?
    var replyContextContent: String?
    var replyContextSenderUsername: String?
    
    init(id: Int64, senderId: UUID, receiverId: UUID, content: String, createdAt: Date, readAt: Date, senderUsername: String? = nil, senderAvatarUrl: String? = nil, replyToId: Int64? = nil, replyContextContent: String? = nil, replyContextSenderUsername: String? = nil) {
        self.id = id
        self.senderId = senderId
        self.receiverId = receiverId
        self.content = content
        self.createdAt = createdAt
        self.readAt = readAt
        self.senderUsername = senderUsername
        self.senderAvatarUrl = senderAvatarUrl
        self.replyToId = replyToId
        self.replyContextContent = replyContextContent
        self.replyContextSenderUsername = replyContextSenderUsername
    }
}
