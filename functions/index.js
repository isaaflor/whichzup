/* Begin, prompt: Explain how to send the push notification using a Firebase Cloud Function */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = functions.firestore
    .document('chats/{chatId}/messages/{messageId}')
    .onCreate(async (snap, context) => {
        const message = snap.data();
        const chatId = context.params.chatId;

        // 1. Fetch the sender's details to display their name
        const senderDoc = await admin.firestore().collection('users').doc(message.senderId).get();
        const senderName = senderDoc.exists ? senderDoc.data().name : "Someone";

        // 2. Fetch the chat to find other participants
        const chatDoc = await admin.firestore().collection('chats').doc(chatId).get();
        const participantIds = chatDoc.data().participantIds;

        // 3. Remove the sender from the notification recipients
        const recipients = participantIds.filter(id => id !== message.senderId);

        // 4. Fetch the FCM tokens of the recipients
        const tokens = [];
        for (const userId of recipients) {
            const userDoc = await admin.firestore().collection('users').doc(userId).get();
            if (userDoc.exists && userDoc.data().fcmToken) {
                tokens.push(userDoc.data().fcmToken);
            }
        }

        if (tokens.length === 0) return null;

        // 5. Build and send the data payload via FCM v1
        const payload = {
            data: {
                senderName: senderName,
                text: message.text || "Sent a media file",
                chatId: chatId
            },
            tokens: tokens
        };

        try {
            const response = await admin.messaging().sendEachForMulticast(payload);
            console.log(response.successCount + ' messages were sent successfully');
        } catch (error) {
            console.error('Error sending notification:', error);
        }
    });
/* End */