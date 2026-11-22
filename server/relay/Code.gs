/**
 * CityFix relay: a Google Apps Script web app that does the few things the free Firebase plan
 * cannot do from the app. It runs as the project owner ("Execute as: Me"), so it needs no
 * service-account key.
 *
 * The app POSTs JSON { action, idToken, ... }. Every action first checks the Firebase ID token.
 * - "push":     send FCM data messages to users (their users/{uid}.fcmToken) and/or a topic.
 * - "sendCode": email a 6-digit sign-in code (second factor) to the signed-in user.
 * - "checkCode": check that code.
 * - "uploadVideo": save a video in Google Drive (instead of Cloud Storage) and return its id.
 *
 * Deploy: Deploy > New deployment > Web app, Execute as "Me", access "Anyone".
 * After editing: Deploy > Manage deployments > Edit > Version "New version" (same URL).
 */
const PROJECT_ID = 'cityfix-8f70e';
const CODE_MINUTES = 10;
const VIDEO_FOLDER = 'CityFix videos';

function doPost(e) {
  const request = JSON.parse(e.postData.contents);
  const sender = verifySender(request.idToken);
  if (!sender) return reply({ error: 'unauthorized' });

  switch (request.action || 'push') {
    case 'push': return reply(push(sender, request));
    case 'sendCode': return reply(sendCode(sender));
    case 'checkCode': return reply(checkCode(sender, request.code));
    case 'uploadVideo': return reply(uploadVideo(request.videoBase64, request.name));
    default: return reply({ error: 'unknown action' });
  }
}

// ---- Push notifications (FCM HTTP v1) ----

function push(sender, request) {
  const data = Object.assign({}, request.data, { senderUid: sender.uid });
  const messages = [];
  (request.toUids || [])
    .filter(function (uid) { return uid !== sender.uid; })
    .forEach(function (uid) {
      const token = fcmTokenOf(uid);
      if (token) messages.push({ token: token, data: data });
    });
  if (request.topic) messages.push({ topic: request.topic, data: data });
  messages.forEach(sendFcm);
  return { sent: messages.length };
}

/** users/{uid}.fcmToken, saved by the app after sign-in. */
function fcmTokenOf(uid) {
  const url = 'https://firestore.googleapis.com/v1/projects/' + PROJECT_ID + '/databases/(default)/documents/users/' + uid;
  const response = UrlFetchApp.fetch(url, { headers: ownerAuth(), muteHttpExceptions: true });
  if (response.getResponseCode() !== 200) return null;
  const fields = JSON.parse(response.getContentText()).fields || {};
  return fields.fcmToken ? fields.fcmToken.stringValue : null;
}

function sendFcm(message) {
  message.android = { priority: 'high' };
  UrlFetchApp.fetch('https://fcm.googleapis.com/v1/projects/' + PROJECT_ID + '/messages:send', {
    method: 'post',
    contentType: 'application/json',
    headers: ownerAuth(),
    payload: JSON.stringify({ message: message }),
    muteHttpExceptions: true,
  });
}

// ---- Sign-in code by email (second factor) ----

/** The code is made and kept here, never in the app; it is emailed to the address of the signed-in account. */
function sendCode(sender) {
  const code = String(Math.floor(100000 + Math.random() * 900000));
  CacheService.getScriptCache().put('code_' + sender.uid, code, CODE_MINUTES * 60);
  MailApp.sendEmail(
    sender.email,
    'CityFix sign-in code',
    'Your CityFix sign-in code is ' + code + '. It is valid for ' + CODE_MINUTES + ' minutes.\n' +
      'If you did not try to sign in, change your password.'
  );
  return { sent: true };
}

/** One use only; a wrong code also deletes it, so guessing needs a new email each time. */
function checkCode(sender, code) {
  const cache = CacheService.getScriptCache();
  const expected = cache.get('code_' + sender.uid);
  cache.remove('code_' + sender.uid);
  return { ok: expected !== null && expected === String(code) };
}

// ---- Videos in Google Drive (instead of Cloud Storage) ----

/** Saves the MP4 in the owner's Drive, readable by anyone with the link, and returns its id. */
function uploadVideo(videoBase64, name) {
  const folders = DriveApp.getFoldersByName(VIDEO_FOLDER);
  const folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(VIDEO_FOLDER);
  const blob = Utilities.newBlob(Utilities.base64Decode(videoBase64), 'video/mp4', name);
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return { fileId: file.getId() };
}

// ---- Helpers ----

/** { uid, email } of a valid Firebase ID token, or null. */
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
  return users.length ? { uid: users[0].localId, email: users[0].email } : null;
}

/** The owner's token; x-goog-user-project bills the calls to the Firebase project (free quota). */
function ownerAuth() {
  return { Authorization: 'Bearer ' + ScriptApp.getOAuthToken(), 'x-goog-user-project': PROJECT_ID };
}

function reply(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

/** Run once from the editor (Run > authorize): asks for the email and Drive permissions and checks them. */
function authorize() {
  Logger.log('Emails left today: ' + MailApp.getRemainingDailyQuota());
  const folders = DriveApp.getFoldersByName(VIDEO_FOLDER);
  const folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(VIDEO_FOLDER);
  Logger.log('Drive folder ready: ' + folder.getName());
}
