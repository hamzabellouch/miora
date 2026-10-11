# Privacy Policy - Miora

Last Updated: October 11, 2026


### 0. Rule 0: Disclaimer on Modified Builds, External APKs & Unofficial Forks:

- **Original Source Integrity:** This privacy policy, safety guarantees, and security features apply strictly and exclusively to the official, unmodified application binaries and source code distributed directly through this official repository ([hamzabellouch/miora](https://github.com/hamzabellouch/miora)) and its verified official releases.
- **Zero Liability for Modified / External Builds:** We (the publisher and maintainer) assume **no responsibility or liability whatsoever** for any modified APKs, third-party forks, repackaged binaries, unofficial distribution channels, or altered projects not originating directly from this official repository. Any installation or use of modified, tampered, or third-party builds is entirely at your own risk, and the publisher assumes no responsibility for any consequences, damages, security compromises, or data breaches that may occur.


### 1. Executive Summary & Overview:

Miora is an Android application designed for direct, offline peer-to-peer (P2P) file and media sharing between nearby devices using Wi-Fi Direct, Local Hotspot, Bluetooth, Local Network (LAN), and QR code pairing.
#### Core Privacy Commitment:
Miora operates **100% on-device and peer-to-peer**. All file transfers occur directly between the sender and receiver devices over local connections without passing through any cloud servers or external intermediaries. We do not collect, store, transmit, share, or sell any personal data, transferred files, media contents, usage logs, device identifiers, or camera captures to external analytics servers or third parties.


### 2. Information Processed On-Device:
To perform its core P2P discovery, pairing, and file transfer features, Miora reads and processes the following information `locally on your device`:

- **Selected Media & Files:** Photos, videos, audio files, documents, and other files explicitly selected by you for sending are read locally and streamed directly to the recipient device you choose. Received files are saved locally in your device's `Downloads/Miora` folder.
- **Nearby Device Discovery Metadata:** Temporary device display names, local IP addresses, ephemeral hotspot credentials, and service ports are exchanged exclusively with nearby devices on the same local network, Wi-Fi Direct group, or Bluetooth range to establish direct connections.
- **Camera Preview Feed (QR Pairing):** Processed in real-time within volatile memory (RAM) strictly to detect and decode `MIORA_WIFI` pairing QR codes. Camera frames are never saved, recorded, stored to disk, or transmitted.
- **Local Transfer History & Preferences:** Transfer history records (file names, sizes, timestamps, and status) and app preferences (such as custom device name, default transfer protocol, theme preferences, and language settings) are stored strictly locally on the device using Android `SharedPreferences`.


### 3. Permissions Used & Their Purposes:
Miora requests specific Android permissions strictly to deliver its offline file sharing and update functionality:

**A. Wi-Fi & Nearby Devices Permissions** (`NEARBY_WIFI_DEVICES`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`)
- **Purpose:** Enables Miora to discover nearby devices, create high-speed Wi-Fi Direct P2P groups, start local-only hotspots (`LocalOnlyHotspot`), and automatically bind to a receiver's temporary Wi-Fi hotspot when pairing via QR code.
- **Scope:** Used exclusively for local device-to-device connectivity (`neverForLocation` flag applied on Android 13+).

**B. Bluetooth Permissions** (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, and legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` on Android 11 and below)
- **Purpose:** Allows Miora to advertise device availability over Bluetooth Low Energy (BLE), scan for nearby Miora peers, and transfer files offline over Bluetooth Classic (RFCOMM) sockets.
- **Scope:** Used strictly for proximity peer discovery and direct Bluetooth file transfers (`neverForLocation` flag applied).

**C. Location Permissions** (`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`)
- **Purpose:** Required by the Android operating system (especially on Android 12 and below) to perform Wi-Fi Direct peer discovery, local hotspot creation, and Bluetooth scanning.
- **Scope:** Miora **never** tracks, logs, stores, or transmits your geographic GPS coordinates. Location access is used solely to satisfy Android OS requirements for local wireless scanning.

**D. Media & Storage Permissions** (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, and legacy `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` on older Android versions)
- **Purpose:** Allows you to browse and select photos, videos, audio tracks, and files to send, and enables Miora to save incoming files into `Downloads/Miora`.
- **Scope:** Miora only accesses files you explicitly select for transfer and the designated output folder.

**E. Camera Permission** (`android.permission.CAMERA`)
- **Purpose:** Enables the app to open the device camera for real-time scanning of receiver QR codes (`QrScannerView`) via Android CameraX for instant Wi-Fi/Hotspot pairing.
- **Scope:** Used exclusively within the interactive QR scanner view. Camera stream data is processed locally in RAM and discarded immediately.

**F. Internet & Network State** (`INTERNET` & `ACCESS_NETWORK_STATE`)
- **Purpose:** Required by Android to open local TCP/UDP sockets (`MioraTransferEngine` on ports `8988`/`8989`) for local P2P transfers, as well as to check for official app updates on GitHub Releases (`UpdateUtil`) and download optional community language packs (`LanguageManager`).
- **Scope:** No personal data, file contents, or telemetry is ever sent over the internet.

**G. Request Install Packages & Post Notifications** (`REQUEST_INSTALL_PACKAGES` & `POST_NOTIFICATIONS`)
- **Purpose:** Displays download progress notifications and prompts the user to install updated APK packages downloaded via the built-in GitHub update checker.
- **Scope:** Used strictly when the user checks for and installs app updates.


### 4. Peer-to-Peer Transfer & Camera Safety Policy:
Miora complies fully with privacy and security best practices regarding local networking and hardware access:

- **Explicit Recipient Consent:** By default, incoming file transfers require explicit user approval (`Accept` / `Decline` prompt displaying sender name, file count, and total size) before any file data is written to storage, unless the user explicitly enables "Auto-accept trusted peers" in Settings.
- **Strict Camera Isolation:** The camera hardware is accessed solely when the user opens the QR code pairing scanner. No background recording or silent frame capture takes place.
- **Zero Cloud Relay:** Files are streamed directly from sender socket to receiver socket over local wireless links. No file ever touches an external server.


### 5. Data Sharing, Analytics & Advertising:

- **No Third-Party Data Sharing:** No personal user data, usage history, or unselected files ever leave your device.
- **No Third-Party Analytics / Tracking:** Miora does not integrate third-party telemetry, crash reporters, tracking SDKs, or analytics services.
- **No Ads:** Miora is 100% free of advertisements.


### 6. Data Retention & Lifecycle:

- **Volatile Buffer Wiping:** All camera frames and 128 KB transfer stream buffers are cleared from RAM immediately after processing.
- **User-Controlled History:** You can clear your transfer history at any time from the Transfers screen. Uninstalling Miora immediately deletes all local transfer logs and preferences stored in `SharedPreferences`.


### 7. Managing Permissions & User Rights:
You maintain full control over all permissions granted to Miora. You may review status inside the in-app **App Requirements** dialog or revoke any permission at any time via Android Settings:

- **Manage Permissions:** `Settings > Apps > Miora > Permissions`
- **Manage Notifications:** `Settings > Apps > Miora > Notifications`


### 8. Contact & Support:
If you have any questions or feedback regarding this Privacy Policy or permission usage, please contact us at:
Email: hamzabellouchcontact@gmail.com


-------------------------------------------



# سياسة الخصوصية - Miora
آخر تحديث: ١١ أكتوبر ٢٠٢٦

### ٠. القاعدة رقم ٠: إخلاء المسؤولية عن التطبيقات المعدلة وحزم APK الخارجية والمشاريع المشتقة:

* **سلامة المصدر الأصلي:** تنطبق سياسة الخصوصية، وضمانات الأمان، والميزات المذكورة في هذه الوثيقة حصرياً وصراحةً على الإصدارات الرسمية غير المعدلة من التطبيق والشفرة المصدرية المنشورة مباشرة عبر هذا المستودع الأصلي المعتمد ([hamzabellouch/miora](https://github.com/hamzabellouch/miora)) وصفحة إصداراته الرسمية فقط.
* **عدم تحمل المسؤولية عن النسخ المعدلة أو الخارجية:** نحن (الناشر والمطور) **لا نتحمل أي مسؤولية قانونية أو أمنية أو تقنية** عن تثبيت أو استخدام أي تطبيقات معدلة، أو حزم APK خارجية، أو نسخ مُعاد تجميعها، أو مشاريع مشتقة ومعدلة لا تنتمي لهذا المستودع الأصلي. كل ما ينتج عن استخدام أو تثبيت نسخ خارجية أو معدلة (من فقدان بيانات، أو ثغرات أمنية، أو برمجيات خبيثة، أو أي أضرار ناجمة) يقع بالكامل على عاتق المستخدم وحده دون أدنى مسؤولية على الناشر.


### ١. الملخص التنفيذي والنظرة العامة:

تطبيق **Miora** هو تطبيق لمشاركة ونقل الملفات والوسائط مباشرة من جهاز إلى جهاز (Peer-to-Peer) لنظام Android بدون الحاجة إلى اتصال بالإنترنت، وذلك باستخدام تقنيات Wi-Fi Direct، ونقطة الاتصال المحلية (Local Hotspot)، والبلوتوث (Bluetooth)، والشبكة المحلية (LAN)، والاقتران السريع عبر رمز QR.
* **الالتزام الأساسي بالخصوصية:** يعمل Miora بنسبة **١٠٠٪ محلياً وبين الأجهزة مباشرة**. تتم جميع عمليات نقل الملفات مباشرة بين جهاز المرسل وجهاز المستلم عبر اتصال محلي دون المرور بأي خوادم سحابية أو وسطاء خارجيين. نحن لا نجمع أو نخزن أو ننقل أو نشارك أو نبيع أي بيانات شخصية أو ملفات منقولة أو سجلات استخدام أو معرّفات الجهاز أو لقطات الكاميرا إلى خوادم تحليلات خارجية أو أطراف ثالثة.


### ٢. المعلومات التي تتم معالجتها على الجهاز:
لتنفيذ ميزاته الأساسية الخاصة باكتشاف الأجهزة القريبة والاقتران ونقل الملفات، يقوم Miora بقراءة ومعالجة المعلومات التالية "محليًا على جهازك":

* **الوسائط والملفات المحددة:** الصور ومقاطع الفيديو والملفات الصوتية والمستندات التي تختارها بنفسك لإرسالها تتم قراءتها محلياً وبثها مباشرة إلى جهاز المستلم الذي تحدده فقط. ويتم حفظ الملفات المستلمة محلياً في مجلد `Downloads/Miora` على جهازك.
* **بيانات اكتشاف الأجهزة القريبة:** يتم تبادل اسم العرض المؤقت للجهاز، وعناوين IP المحلية، وبيانات نقطة الاتصال المؤقتة، وأرقام المنافذ حصرياً مع الأجهزة القريبة المتصلة بنفس الشبكة المحلية أو نطاق البلوتوث بهدف إنشاء الاتصال المباشر فقط.
* **بث المعاينة من الكاميرا (للاقتران عبر QR):** يتم معالجته في الوقت الفعلي داخل الذاكرة المؤقتة (RAM) فقط لاكتشاف وفك شفرة رموز QR الخاصة بالاتصال (`MIORA_WIFI`). لا يتم حفظ إطارات الكاميرا أو تسجيلها أو تخزينها على القرص أو نقلها مطلقاً.
* **سجل النقل المحلي والتفضيلات:** يتم تخزين سجل عمليات النقل (أسماء الملفات، أحجامها، التوقيت، والحالة) وتفضيلات التطبيق (مثل اسم الجهاز، وبروتوكول النقل الافتراضي، وإعدادات المظهر واللغة) محليًا على الجهاز باستخدام Android `SharedPreferences`.


### ٣. الأذونات المستخدمة وأغراضها:
يطلب Miora أذونات Android محددة فقط لتقديم وظائفه الأساسية في مشاركة الملفات وتحديث التطبيق:

**أ. أذونات الشبكة اللاسلكية والأجهزة القريبة** (`NEARBY_WIFI_DEVICES`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`)
* **الغرض:** تمكين Miora من اكتشاف الأجهزة القريبة، وإنشاء مجموعات اتصال مباشر عالية السرعة عبر Wi-Fi Direct، وتشغيل نقطة اتصال محلية مؤقتة (`LocalOnlyHotspot`)، والاتصال التلقائي بنقطة اتصال المستلم عند مسح رمز QR.
* **النطاق:** تُستخدم حصرياً للاتصال المحلي المباشر بين الأجهزة دون استخدامها لتحديد الموقع (`neverForLocation`).

**ب. أذونات البلوتوث** (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, بالإضافة إلى `BLUETOOTH` و `BLUETOOTH_ADMIN` في الإصدارات الأقدم)
* **الغرض:** السماح للتطبيق بالإعلان عن توفر الجهاز عبر تقنية البلوتوث منخفض الطاقة (BLE)، والبحث عن أجهزة Miora القريبة، ونقل الملفات بدون إنترنت عبر مآخذ البلوتوث (RFCOMM).
* **النطاق:** تُستخدم بشكل صارم لاكتشاف الأجهزة القريبة ونقل الملفات عبر البلوتوث.

**ج. أذونات الموقع الجغرافي** (`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`)
* **الغرض:** يشترط نظام التشغيل Android (خصوصاً في إصدارات Android 12 وما قبله) تفعيل إذن الموقع للسماح بالبحث عن شبكات Wi-Fi Direct ونقاط الاتصال المحلية وأجهزة البلوتوث القريبة.
* **النطاق:** لا يقوم Miora **مطلقاً** بتتبع أو تسجيل أو تخزين أو إرسال إحداثيات موقعك الجغرافي (GPS)، ويقتصر استخدام الإذن على تلبية متطلبات نظام Android لاكتشاف الأجهزة اللاسلكية القريبة.

**د. أذونات الوسائط والتخزين** (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, و `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` في الإصدارات الأقدم)
* **الغرض:** السماح لك باختيار الصور ومقاطع الفيديو والصوتيات والملفات لإرسالها، وتمكين التطبيق من حفظ الملفات المستلمة في مجلد `Downloads/Miora`.
* **النطاق:** لا يصل التطبيق إلا إلى الملفات التي تختارها بنفسك ومجلد الحفظ المخصص.

**هـ. إذن الكاميرا** (`android.permission.CAMERA`)
* **الغرض:** يسمح لـ Miora بفتح كاميرا الجهاز لمسح رمز QR الخاص بجهاز المستلم في الوقت الفعلي عبر مكتبة Android CameraX لإتمام الاقتران الفوري.
* **النطاق:** يُستخدم حصريًا داخل شاشة ماسح رمز QR التفاعلية، وتتم معالجة الإطارات محليًا في الذاكرة المؤقتة والتخلص منها فورًا.

**و. إذن الإنترنت وحالة الشبكة** (`INTERNET` & `ACCESS_NETWORK_STATE`)
* **الغرض:** مطلوب من قِبل نظام Android لفتح مآخذ الشبكة المحلية (TCP/UDP Sockets على المنفذين `8988` و`8989`) لنقل الملفات محلياً، وكذلك للتحقق من وجود تحديثات رسمية للتطبيق عبر GitHub وتنزيل حزم اللغات المجتمعية الاختيارية.
* **النطاق:** لا يتم إرسال أي ملفات شخصية أو بيانات تتبع عبر الإنترنت على الإطلاق.

**ز. إذن تثبيت الحزم وإرسال الإشعارات** (`REQUEST_INSTALL_PACKAGES` & `POST_NOTIFICATIONS`)
* **الغرض:** عرض إشعارات تقدم تنزيل التحديثات ومطالبة المستخدم بتثبيت حزمة التحديث (APK) عند رغبته في تحديث التطبيق.
* **النطاق:** يُستخدم حصرياً عند اختيار المستخدم لتحديث التطبيق.


### ٤. سياسة أمان النقل المباشر والكاميرا:
يلتزم Miora التزامًا كاملًا بأفضل ممارسات الخصوصية والأمان:

* **موافقة المستلم الصريحة:** يتطلب استلام أي ملفات موافقة صريحة من المستلم (نافذة تأكيد تعرض اسم المرسل وعدد الملفات وحجمها الإجمالي مع خيارَي قبول أو رفض) قبل كتابة أي بيانات على جهازك، ما لم تقم بتفعيل خيار "القبول التلقائي" يدوياً من الإعدادات.
* **عزلة صارمة للكاميرا:** يتم الوصول إلى الكاميرا فقط عندما يفتح المستخدم شاشة مسح رمز QR للاقتران. لا يتم أي تسجيل في الخلفية أو التقاط صامت للإطارات.
* **انعدام الوسطاء السحابيين:** تنتقل الملفات مباشرة من مأخذ المرسل إلى مأخذ المستلم عبر الاتصال اللاسلكي المحلي دون المرور بأي خادم خارجي.


### ٥. مشاركة البيانات والتحليلات والإعلانات:

* **عدم مشاركة البيانات مع أطراف ثالثة:** لا تغادر بياناتك الشخصية أو سجلاتك إلى أي جهة خارجية.
* **عدم وجود تحليلات أو تتبع:** لا يدمج Miora أي خدمات Telemetry أو SDKs للتتبع أو التحليلات.
* **خالٍ تماماً من الإعلانات:** لا يعرض Miora أي إعلانات تجارية أو موجهة.


### ٦. الاحتفاظ بالبيانات ودورة حياتها:

* **مسح الذاكرة المؤقتة:** يتم مسح جميع إطارات الكاميرا ومخازن البث المؤقتة (Buffers) من الذاكرة (RAM) فور انتهاء المعالجة أو النقل.
* **التحكم الكامل في السجل:** يمكنك مسح سجل عمليات النقل في أي وقت من داخل تبويب "التحويلات" (Transfers)، كما يؤدي إلغاء تثبيت التطبيق إلى حذف جميع السجلات والتفضيلات المخزنة في `SharedPreferences` فوراً وبشكل دائم.


### ٧. إدارة الأذونات وحقوق المستخدم:
تحتفظ بالتحكم الكامل في الأذونات الممنوحة لـ Miora. يمكنك مراجعة حالة الأذونات من نافذة **متطلبات التطبيق** داخل التطبيق أو إلغاء أي إذن في أي وقت عبر إعدادات Android:

* **إدارة الأذونات:** `الإعدادات > التطبيقات > Miora > الأذونات`
* **إدارة الإشعارات:** `الإعدادات > التطبيقات > Miora > الإشعارات`


### ٨. التواصل والدعم:
إذا كانت لديك أي أسئلة أو ملاحظات بشأن سياسة الخصوصية هذه أو استخدام الأذونات، فيرجى التواصل معنا على:
البريد الإلكتروني: hamzabellouchcontact@gmail.com
