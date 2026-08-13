/*
 * Firebase Cloud Messaging service worker.
 * Replace the values below with the public web configuration from Firebase Console.
 * This file is intentionally separate: service workers cannot read Vite's .env at runtime.
 */
importScripts('https://www.gstatic.com/firebasejs/12.9.0/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/12.9.0/firebase-messaging-compat.js')

firebase.initializeApp({
  apiKey: "AIzaSyDSsPn9nc0dJ5JIdpzc3f_ysB_E8d7WDlU",
  authDomain: "nudgee-99.firebaseapp.com",
  projectId: "nudgee-99",
  storageBucket: "nudgee-99.firebasestorage.app",
  messagingSenderId: "381441435963",
  appId: "1:381441435963:web:101cd6ec943d8ea661ef1e",
})

const messaging = firebase.messaging()
messaging.onBackgroundMessage((payload) => {
  const notification = payload.notification ?? {}
  self.registration.showNotification(notification.title ?? 'Nudgee', {
    body: notification.body ?? '알림 시간이 되었습니다.',
    data: payload.data,
  })
})