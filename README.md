# Toast
Toast is a mobile application designed for social groups and university students to plan, share, and manage events seamlessly. The app enables private event creation, group interaction, RSVP tracking, and media sharing — all supported by a secure backend API.
<div align="center">
  <img width="500" height="500" alt="toast_logo" src="https://github.com/user-attachments/assets/c74a8818-1795-472a-84e8-6cc9c2aaa498" />
</div>



## 1: Requirements

You will require the following in order to use this application:

* Android Studio 2020.3 or later
* Android SDK 29 or later
* A device or emulator running Android 7.0 (Nougat) or later
* Internet connection for accessing events, RSVP, and Google Drive media sharing
* Firebase project setup for Authentication and Firestore

---

## 2: How to Apply / Install

This is how the application can be installed and used:

1. Download the source code from the GitHub repository.
2. Open Android Studio and select **Open an existing project**, then locate the project folder.
3. Sync the project with Gradle files to ensure all dependencies are downloaded.
4. Configure `google-services.json` for Firebase.
5. Build and run the application on an Android device or emulator.
6. Alternatively, download the APK file (once published) and install it directly on an Android device.

---

## 3: Functionality

The following is how the application works:

* **Secure Authentication:**
  Users can register and log in via Google Single Sign-On (Firebase Auth), normal Email/Password Authentication or Biometric login.

* **Event Management:**
  Users can create, edit, and delete private events with details such as name, description, category, location, and preferences.

* **RSVP & Attendance Tracking:**
  Users can RSVP to events (Going, Not Going, Maybe).

* **Media Sharing:**
  Users can access and share images and videos taken throughout the events via a Google Drive link that the host creates during event creation. This link can only be accessible to guests once the user has created the google drive folder as well as the event. 

* **Notifications:**
  Users receive updates for RSVP changes, event edits, and group activity.

* **Event Sharing:**
  Users can copy the link that is generated when they create an event to whoever they wish to share it with. 

* **Profile & Settings:**
  Users can update personal details, change passwords or delete their account.

---

## 4: Non-Functional Requirements

These are how the system performs:

* Provides a stable and responsive user experience with minimal downtime.
* Follows RESTful API standards for efficient communication between app and backend.
* Secure authentication and data handling through Firebase Auth and Firestore.
* Hosting on Render ensures availability and scalability of backend API.
* User-friendly interface with clear navigation between screens.

---

## 5: Features

The following is the features:

* **Google drive link:**
  Users can share their google dirve folder link and guests can upload images to that link.

* **RSVP Form:**
  Guests are sent a RSVP formn where they can notify the host if they are attening or not and a live attendee count updates.

* **Poll:**
  Users can create a dietary requirements poll as well as a music suggestiosn poll so guests to interact with.

* **Countdown:**
  Users are notified with a live countdown of how many days, hours and minutes are left until the event. 

---

## 6: Credits

This application was created by **The Toast Team**

* Angenalise Elisha Stephen
* Annabel Govender
* Amishka Solomon
* Liam Pather
* Jucal Maistry

---

## 7: Google Play Store

The app will be published on the Google Play Store for public download.

* Users will be able to access the app easily, receive automatic updates, and view app ratings and screenshots.
* Play Store listing will include full description, screenshots, and promotional content.

---

## 9: GitHub Link
[App Repository](https://github.com/ST10291541/Technovate)

---

## 9: References

* How do REST APIs communicate with databases?: (https://moldstud.com/articles/p-how-do-rest-apis-communicate-with-databases)
* Integrating Restful Apis With Frontend: (https://frontenddeveloper.io/insight/integrating-restful-apis-with-frontend/)
* Web Services: (https://render.com/docs/web-services)
* Render Documentation: (https://render.com)
* Firebase Documentation: (https://firebase.google.com)
* Android Developers Documentation: (https://developer.android.com)

---

## AI Usage
ChatGPT was used to fix .xml layout files to ensure consistency throughout the application. It was used to assist with error problems that occured when running the app by specifying which file had the problem. It was used to fix colours, misspelled code linking etc. 
