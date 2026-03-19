const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendChatNotification = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
    const messageData = event.data.data();
    const chatId = event.params.chatId;
    const senderId = messageData.senderId;

    const chatDoc = await admin.firestore().collection("chats").doc(chatId).get();
    const chatData = chatDoc.data();
    
    const recipients = chatData.participantIds.filter(id => id !== senderId);

    for (const recipientId of recipients) {
        const userDoc = await admin.firestore().collection("users").doc(recipientId).get();
        const fcmToken = userDoc.data()?.fcmToken;

        if (fcmToken) {
            const payload = {
                token: fcmToken,
                data: {
                    chatId: chatId,
                    senderName: "Nova Mensagem", 
                    text: messageData.text || "Enviou um anexo",
                    isGroup: chatData.isGroup ? "true" : "false",
                    groupName: chatData.name || ""
                }
            };
            await admin.messaging().send(payload);
        }
    }
});