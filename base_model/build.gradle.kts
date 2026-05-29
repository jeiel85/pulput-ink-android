plugins {
  id("com.android.asset-pack")
}

assetPack {
  packName.set("base_model")
  dynamicDelivery {
    // fast-follow: Play downloads the model automatically right after install,
    // without requiring the user to trigger it. Files are extracted to disk so
    // whisper.cpp can open them by absolute path. (install-time packs may stay
    // compressed in the APK, which would not work for a native file path.)
    deliveryType.set("fast-follow")
  }
}
