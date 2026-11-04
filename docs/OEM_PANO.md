# OEM PI top-view integration

The active renderer is VehiclePanoView, not FisheyeGLView. Its former startup fixed the model to sc2e/sc2e_right, sensor to 1/0, and selected PORTRAIT_RIGH_3D_TOP (4886). It now selects LANDSCAPE_FRONT (0), the combined front/top viewpoint mapped by OEM PIAPI for front mode, and crops the top-view column.

OemPanoProfile first reads the OEM initSdkParam.json when permitted. Otherwise it reads model_value and panorama_online from CarSettings.UserTableData, steering side from CustomVehicleConfig, sensor properties, and model constants from the installed AVC APK. This fallback supports the explicitly mapped PI families; an unknown family/engine is rejected. It does not start OEM controllers in this process. Selected resources are extracted recursively from the installed APK into a versioned app-private cache. Readable per-car parameters are copied before native use; originals are never modified.

Native libraries are copied from AVC into the app namespace. This car's unmodified VehiclePano cannot link _Unwind_Resume. Only that error permits the existing bundled PI compatibility adapter fallback. No OEM installed binary is patched. Other missing symbols/ABIs fail with a diagnostic instead of starting the camera thread.

Tested on connected DiLink 3 / Atto 3: detected sc2e/sc2e_right, 960P, cameraType 8/0 (formerly hardcoded 1/0). The app starts and renders a live top-view PiP. Screenshots were inspected. Native fallback was necessary. Other vehicle families and display layouts have not been tested.

The current car denies access to /collect2/autovideo. Its /proc/camera_vehicle/optics endpoint is absent. Calibration therefore uses that OEM APK's default assets, not confirmed per-car calibration. Pixel-identical agreement with OEM AVC is not established. Crop and fixed 1920x972 render geometry are still specific to the tested PI landscape layout; engine families such as APA/BST/AVM require separate adapters.

Diagnostics: pano_profile_summary, pano_profile_ready, pano_profile_error in pnp preferences. Source references: decompiled OEM pano/PIAPI, panosdk/PIAPI, DeviceStates, CarTypeList and PanoResourceController from /system/app/AutoVideo/AutoVideo.apk. Inspected original dex SHA-256: 0f0322dee23ce4fd2c3af5aec913dfe7e94eca80d878daaa487e061bf5388acb and da1595922109a9546ff19517ac94e945ddf2b936b31b15eafa0c50ab27ade206.
