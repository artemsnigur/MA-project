/**
 * CityFix push relay: a Google Apps Script web app (free, no Firebase Blaze plan needed).
 *
 * The app POSTs JSON: { idToken, toUids: [uid, ...], topic: "operators" | null, data: {...} }
 * 1. The Firebase ID token proves who is sending (Identity Toolkit accounts:lookup).
 * 2. The FCM token of each recipient is read from users/{uid}.fcmToken in Firestore.
 * 3. A data message is sent with the FCM HTTP v1 API; the app builds the notification text.
 *
 * It runs as the project owner ("Execute as: Me"), so it uses the owner's OAuth token and
 * needs no service-account key. Deploy: Deploy > New deployment > Web app, access "Anyone".
 */
const PROJECT_ID = 'cityfix-8f70e';

function doPost(e) {
  const request = JSON.parse(e.postData.contents);
  const senderUid = verifySender(request.idToken);
  if (!senderUid) return reply({ error: 'unauthorized' });

  const data = Object.assign({}, request.data, { senderUid: senderUid });
  const messages = [];
  (request.toUids || [])
    .filter(function (uid) { return uid !== senderUid; })
    .forEach(function (uid) {
      const token = fcmTokenOf(uid);
      if (token) messages.push({ token: token, data: data });
    });
  if (request.topic) messages.push({ topic: request.topic, data: data });

  messages.forEach(send);
  return reply({ sent: messages.length });
}

/** The uid of a valid Firebase ID token, or null. */
function verifySender(idToken) {
  if (!idToken) return null;
  const response = UrlFetchApp.fetch('https://identitytoolkit.googleapis.com/v1/projects/' + PROJECT_ID + '/accounts:lookup', {
    method: 'post',
    contentType: 'application/json',
    headers: ownerAuth(),
    payload: JSON.stringify({ idToken: idToken }),
    muteHttpExceptions: true,
  });
  if (response.getResponseCode() !== 200) return null;
  const users = JSON.parse(response.getContentText()).users || [];
  return users.length ? users[0].localId : null;
}

/** users/{uid}.fcmToken, saved by the app after sign-in. */
function fcmTokenOf(uid) {
  const url = 'https://firestore.googleapis.com/v1/projects/' + PROJECT_ID + '/databases/(default)/documents/users/' + uid;
  const response = UrlFetchApp.fetch(url, { headers: ownerAuth(), muteHttpExceptions: true });
  if (response.getResponseCode() !== 200) return null;
  const fields = JSON.parse(response.getContentText()).fields || {};
  return fields.fcmToken ? fields.fcmToken.stringValue : null;
}

function send(message) {
  message.android = { priority: 'high' };
  UrlFetchApp.fetch('https://fcm.googleapis.com/v1/projects/' + PROJECT_ID + '/messages:send', {
    method: 'post',
    contentType: 'application/json',
    headers: ownerAuth(),
    payload: JSON.stringify({ message: message }),
    muteHttpExceptions: true,
  });
}

/** The owner's token; x-goog-user-project bills the calls to the Firebase project (free quota). */
function ownerAuth() {
  return { Authorization: 'Bearer ' + ScriptApp.getOAuthToken(), 'x-goog-user-project': PROJECT_ID };
}

function reply(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}
