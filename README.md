# FreshCart — Grocery Buying App (Developer Guide)

This is the developer reference and architecture documentation for the **FreshCart** Android application. Note: The app's user-facing UI never exposes any backend, database, or library names.

---

## 1. Remote Database Project & Collections

### Database Architecture
- **Backend Service**: Firebase Realtime Database
- **Project ID**: `hack-94d42`
- **Database URL**: `https://hack-94d42-default-rtdb.firebaseio.com`

### Database Nodes (Collections)
1. `/products`:
   - `id` (String): Unique product identifier (e.g. `prod_1`)
   - `name` (String): Product display title (e.g. `Fresh Red Apples`)
   - `description` (String): Comprehensive product description
   - `category` (String): Category name (e.g. `Fruits`, `Vegetables`, `Dairy & Eggs`)
   - `price` (Double): Unit price (e.g. `149.00`)
   - `stock` (Int): Current inventory count (`> 5`: In Stock, `1..5`: Low Stock, `<= 0`: Out of Stock)
   - `imageUrl` (String): Direct raw image URL hosted on GitHub
   - `createdAt` (Long): Timestamp (epoch milliseconds)
   - `updatedAt` (Long): Timestamp (epoch milliseconds)

2. `/categories`:
   - `id` (String): Category identifier (e.g. `cat_fruits`)
   - `name` (String): Display name (e.g. `Fruits`)
   - `order` (Int): Sorting sequence in category filter bar

3. `/orders`:
   - `id` (String): Unique order code (e.g. `ORD-E3F78A12`)
   - `customerId` (String): Device anonymous customer ID (e.g. `CUST-9B8A7C6D`)
   - `items` (List):
     - `productId` (String)
     - `name` (String)
     - `price` (Double)
     - `qty` (Int)
   - `total` (Double): Total checkout price
   - `status` (String): Live status (`Pending` -> `Packed` -> `Out for Delivery` -> `Delivered`, or `Cancelled`)
   - `createdAt` (Long): Timestamp (epoch milliseconds)

4. `/config`:
   - `adminPassword` (String): Secure password string (or SHA-256 hash) for store management access.

---

## 2. Where to Set Database Credentials & Config

- **Android Client Config**: `app/google-services.json`
- **Programmatic Initialization**: `app/src/main/java/com/hack/FreshCartApplication.kt` and `app/src/main/java/com/hack/data/repository/GroceryRepository.kt`
- Both use the database URL:
  ```kotlin
  FirebaseDatabase.getInstance("https://hack-94d42-default-rtdb.firebaseio.com")
  ```

---

## 3. How the Admin Password is Configured & Changed

- The active admin password is stored in the database node: `/config/adminPassword`.
- **Default password**: `admin123`
- **To change the password**:
  1. Open the database console or send an HTTP PUT request:
     ```bash
     curl -X PUT -d '"new_secure_password"' https://hack-94d42-default-rtdb.firebaseio.com/config/adminPassword.json
     ```
  2. The application verifies both plaintext passwords and SHA-256 hashed passwords in `GroceryRepository.kt:verifyAdminPassword()`.

---

## 4. Supported GitHub Raw Image URL Format

The app accepts and smoothly renders direct GitHub raw content URLs:
```
https://raw.githubusercontent.com/<user>/<repo>/<branch>/<path>/<filename>.jpg
```
For example:
```
https://raw.githubusercontent.com/Daksh159357/photo/main/fresh_apples.jpg
```
*Note*: The app automatically cleans up browser URLs formatted as `https://github.com/.../blob/...` by converting them to the raw format automatically when saved by an admin.

Supported image formats:
- `.jpg`, `.jpeg`, `.png`, `.webp`

---

## 5. Steps to Rebuild the APK

The project is configured for automated cloud builds using GitHub Actions:

### Option A: GitHub Actions (Recommended)
1. Push any commit to the `main` branch:
   ```bash
   git add .
   git commit -m "Update code"
   git push origin main
   ```
2. The workflow in `.github/workflows/build-apk.yml` triggers automatically on GitHub's cloud runners.
3. Once completed, download `FreshCart-v1.0.apk` from the **Actions** tab artifacts or the **Releases** page (`v1.0.0`).

### Option B: Local Build
```bash
./gradlew assembleDebug
```
The output APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```
