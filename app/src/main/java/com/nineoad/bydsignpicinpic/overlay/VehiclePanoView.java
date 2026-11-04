package com.nineoad.bydsignpicinpic.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.pi.vehiclepano.PanoAPI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OEM PI rendering with model-specific AVC assets and a cropped landscape top view. */
public class VehiclePanoView extends FrameLayout {
    private static final String TAG = "VehiclePanoView";

    // Fixed off-screen canvas size where libVehiclePano.so renders its native Landscape layout (1920x972)
    public static final int PANO_BUFFER_W = 1920;
    public static final int PANO_BUFFER_H = 972;

    // Default crop for the tested landscape PI layout. Other OEM layouts may require tuning.
    public static final float DEF_CROP_LEFT = 0.686f;
    public static final float DEF_CROP_TOP = 0.000f;
    public static final float DEF_CROP_RIGHT = 1.000f;
    public static final float DEF_CROP_BOTTOM = 1.000f;

    public interface SurfaceReadyListener {
        void onCameraSurfaceReady(Surface cameraSurface);
        void onCameraSurfaceDestroyed();
        default void onCameraSurfaceError() { onCameraSurfaceDestroyed(); }
    }

    private static final String VERT =
        "attribute vec4 aPos;\n" +
        "attribute vec2 aUv;\n" +
        "varying vec2 vUv;\n" +
        "void main() {\n" +
        "  gl_Position = aPos;\n" +
        "  vUv = aUv;\n" +
        "}\n";

    private static final String FRAG =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "varying vec2 vUv;\n" +
        "uniform samplerExternalOES uTex;\n" +
        "uniform mat4 uSt;\n" +
        "uniform vec4 uCrop;\n" +
        "uniform vec4 uBevAngles;\n" +
        "uniform float uAspect;\n" +
        "uniform vec2 uWindowSize;\n" +
        "uniform float uRadius;\n" +
        "vec2 turnUv(vec2 uv, vec2 center, float angle) {\n" +
        "  vec2 d = (uv - center) * vec2(uAspect, 1.0);\n" +
        "  float c = cos(angle), sn = sin(angle);\n" +
        "  return center + vec2(c*d.x - sn*d.y, sn*d.x + c*d.y) / vec2(uAspect, 1.0);\n" +
        "}\n" +
        "void main() {\n" +
        "  if (uRadius > 0.5) {\n" +
        "    vec2 halfSize = uWindowSize * 0.5;\n" +
        "    float r = min(uRadius, min(halfSize.x, halfSize.y));\n" +
        "    vec2 p = abs(gl_FragCoord.xy - halfSize) - (halfSize - vec2(r));\n" +
        "    float d = length(max(p, 0.0)) - r;\n" +
        "    if (d > 0.0) {\n" +
        "      discard;\n" +
        "    }\n" +
        "  }\n" +
        "  vec2 uv = vUv;\n" +
        "  float midY = smoothstep(0.12, 0.32, vUv.y) * (1.0 - smoothstep(0.68, 0.88, vUv.y));\n" +
        "  uv = turnUv(uv, vec2(0.5, 0.86), uBevAngles.x * smoothstep(0.58, 0.92, vUv.y));\n" +
        "  uv = turnUv(uv, vec2(0.5, 0.14), uBevAngles.y * (1.0 - smoothstep(0.08, 0.42, vUv.y)));\n" +
        "  uv = turnUv(uv, vec2(0.16, 0.5), uBevAngles.z * (1.0 - smoothstep(0.08, 0.42, vUv.x)) * midY);\n" +
        "  uv = turnUv(uv, vec2(0.84, 0.5), uBevAngles.w * smoothstep(0.58, 0.92, vUv.x) * midY);\n" +
        "  uv = clamp(uv, vec2(0.0), vec2(1.0));\n" +
        "  vec2 cuv = vec2(mix(uCrop.x, uCrop.z, uv.x), mix(uCrop.y, uCrop.w, uv.y));\n" +
        "  vec2 tc = (uSt * vec4(cuv, 0.0, 1.0)).xy;\n" +
        "  if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) {\n" +
        "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  } else {\n" +
        "    gl_FragColor = texture2D(uTex, tc);\n" +
        "  }\n" +
        "}\n";

    private GLSurfaceView glView;
    private PanoRenderer renderer;
    private ImageView carOverlayView;
    private PanoAPI panoApi;
    private SurfaceTexture cameraTexture;
    private Surface cameraSurface;
    private SurfaceReadyListener readyListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isInitializing = false;
    private volatile boolean isPanoReady = false;
    private volatile boolean activeOnScreen = false;
    private volatile float cornerRadiusPx = 0.0f;

    public static final int VP_3D_TOP = PanoAPI.LANDSCAPE_FRONT; // Same combined front + top view as OEM front mode, not an orbiting 3D camera.
    public static final int VP_3D_TOP_RIGHT = VP_3D_TOP;

    private int currentViewpoint = VP_3D_TOP;
    private SharedPreferences prefs;

    private volatile float cropLeft = DEF_CROP_LEFT;
    private volatile float cropTop = DEF_CROP_TOP;
    private volatile float cropRight = DEF_CROP_RIGHT;
    private volatile float cropBottom = DEF_CROP_BOTTOM;
    private volatile float angleFront, angleRear, angleLeft, angleRight;

    private volatile float carScaleW = 0.33f;
    private volatile float carAspectH = 2.30f;
    private volatile float carOffX = 0.0f;
    private volatile float carOffY = 0.025f;
    private volatile boolean showCustomCarOverlay = false;
    private volatile long lastStandbyFrameMs = 0L;

    public VehiclePanoView(Context context) {
        super(context);
        init(context);
    }

    public VehiclePanoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        this.prefs = context.getSharedPreferences("pnp", Context.MODE_PRIVATE);
        this.currentViewpoint = VP_3D_TOP;
        reloadCropPrefs();

        setClipChildren(true);
        setClipToPadding(true);

        this.glView = new GLSurfaceView(context);
        this.glView.setEGLContextClientVersion(2);
        this.glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        this.glView.setZOrderOnTop(false);
        this.glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        this.glView.setPreserveEGLContextOnPause(true);
        this.renderer = new PanoRenderer();
        this.glView.setRenderer(this.renderer);
        this.glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        this.glView.setClickable(false);
        this.glView.setFocusable(false);
        addView(this.glView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int carRes = CarModelManager.getSelectedCarModelDrawable(context);
        this.carOverlayView = new ImageView(context);
        this.carOverlayView.setImageResource(carRes);
        this.carOverlayView.setScaleType(ImageView.ScaleType.FIT_XY);
        this.carOverlayView.setClickable(false);
        this.carOverlayView.setFocusable(false);
        this.carOverlayView.setVisibility(View.INVISIBLE);
        addView(this.carOverlayView);
    }

    public void setCornerRadiusPx(float radiusPx) {
        this.cornerRadiusPx = radiusPx;
        if (this.glView != null) {
            this.glView.requestRender();
        }
    }

    public void setActiveOnScreen(boolean active) {
        if (this.activeOnScreen != active) {
            this.activeOnScreen = active;
            updateCarOverlayVisibility();
            requestLayout();
            invalidate();
            if (this.glView != null) {
                this.glView.requestRender();
            }
        } else if (active && this.glView != null) {
            this.glView.requestRender();
        }
    }

    private void updateCarOverlayVisibility() {
        if (this.carOverlayView != null) {
            boolean show = this.activeOnScreen && this.showCustomCarOverlay;
            this.carOverlayView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public boolean isActiveOnScreen() {
        return this.activeOnScreen;
    }

    public void reloadCropPrefs() {
        if (this.prefs != null) {
            // Migrate legacy crop prefs to the exact Landscape BEV column (v4)
            if (this.prefs.getInt("bev_crop_schema_ver", 1) < 4) {
                this.prefs.edit()
                    .putInt("bev_crop_schema_ver", 4)
                    .putFloat("bev_crop_left", DEF_CROP_LEFT)
                    .putFloat("bev_crop_top", DEF_CROP_TOP)
                    .putFloat("bev_crop_right", DEF_CROP_RIGHT)
                    .putFloat("bev_crop_bottom", DEF_CROP_BOTTOM)
                    .putBoolean("bev_custom_car_overlay", false)
                    .apply();
            }
            this.cropLeft = this.prefs.getFloat("bev_crop_left", DEF_CROP_LEFT);
            this.cropTop = this.prefs.getFloat("bev_crop_top", DEF_CROP_TOP);
            this.cropRight = this.prefs.getFloat("bev_crop_right", DEF_CROP_RIGHT);
            this.cropBottom = this.prefs.getFloat("bev_crop_bottom", DEF_CROP_BOTTOM);
            this.angleFront = clampAngle(this.prefs.getFloat("bev_angle_front", 0f));
            this.angleRear = clampAngle(this.prefs.getFloat("bev_angle_rear", 0f));
            this.angleLeft = clampAngle(this.prefs.getFloat("bev_angle_left", 0f));
            this.angleRight = clampAngle(this.prefs.getFloat("bev_angle_right", 0f));

            this.carScaleW = this.prefs.getFloat("bev_car_scale_w", 0.35f);
            this.carAspectH = this.prefs.getFloat("bev_car_aspect_h", 2.41f);
            this.carOffX = this.prefs.getFloat("bev_car_off_x", 0.0f);
            this.carOffY = this.prefs.getFloat("bev_car_off_y", 0.008f);
            int carColor = this.prefs.getInt("car_model_color", 0);
            this.showCustomCarOverlay = this.prefs.getBoolean("bev_custom_car_overlay", false);

            if (this.carOverlayView != null) {
                int carRes = CarModelManager.getSelectedCarModelDrawable(getContext());
                this.carOverlayView.setImageResource(carRes);
                updateCarOverlayVisibility();
            }
        }
    }

    private static float clampAngle(float degrees) {
        return (float) Math.toRadians(Math.max(-15f, Math.min(15f, degrees)));
    }

    /**
     * Returns the exact pixel aspect ratio (width / height) of the cropped BEV region
     * inside the fixed off-screen Pano buffer (1920x972 landscape buffer), so OverlayService
     * can size the PIP window to match 1:1 without any stretching when resized.
     */
    public float getCroppedAspectRatio() {
        reloadCropPrefs();
        float cw = Math.max(0.05f, this.cropRight - this.cropLeft) * (float) PANO_BUFFER_W;
        float ch = Math.max(0.05f, this.cropBottom - this.cropTop) * (float) PANO_BUFFER_H;
        return cw / ch;
    }

    public void reapplyTuning() {
        reloadCropPrefs();
        requestLayout();
        invalidate();
        if (this.glView != null) {
            this.glView.requestRender();
        }
    }

    public void setSurfaceReadyListener(SurfaceReadyListener listener) {
        this.readyListener = listener;
        if (this.cameraSurface != null && this.isPanoReady && listener != null) {
            listener.onCameraSurfaceReady(this.cameraSurface);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);

        if (w > 0 && h > 0) {
            reloadCropPrefs();
            if (this.glView != null) {
                this.glView.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                );
            }
            if (this.carOverlayView != null) {
                int carW = (int) (w * this.carScaleW);
                int carH = (int) (carW * this.carAspectH);
                this.carOverlayView.measure(
                    MeasureSpec.makeMeasureSpec(carW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(carH, MeasureSpec.EXACTLY)
                );
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;

        reloadCropPrefs();
        if (this.glView != null) {
            this.glView.layout(0, 0, w, h);
        }

        if (this.carOverlayView != null) {
            int carW = (int) (w * this.carScaleW);
            int carH = (int) (carW * this.carAspectH);
            int centerX = (int) (w / 2.0f + this.carOffX * w);
            int centerY = (int) (h / 2.0f + this.carOffY * h);
            int carLeft = centerX - (carW / 2);
            int carTop = centerY - (carH / 2);
            this.carOverlayView.layout(carLeft, carTop, carLeft + carW, carTop + carH);
        }
    }

    private static FloatBuffer fb(float[] fArr) {
        FloatBuffer buf = ByteBuffer.allocateDirect(fArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(fArr).position(0);
        return buf;
    }

    private final class PanoRenderer implements GLSurfaceView.Renderer {
        private int program;
        private int aPos;
        private int aUv;
        private int uTex;
        private int uSt;
        private int uCrop;
        private int uBevAngles;
        private int uAspect;
        private int uWindowSize;
        private int uRadius;
        private int oesTexId;
        private SurfaceTexture panoDisplayTexture;
        private Surface panoDisplaySurface;
        private final float[] stMatrix = new float[16];
        private final FloatBuffer quadPos = fb(new float[]{-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f});
        // SurfaceTexture.getTransformMatrix(stMatrix) on Android already flips Y (mapping v=0 to bottom of screen, v=1 to top of screen).
        // Therefore, quadUv MUST be {0,0, 1,0, 0,1, 1,1} so the image is right-side up (front of car pointing UP)!
        private final FloatBuffer quadUv = fb(new float[]{0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f});
        private int viewW = 1;
        private int viewH = 1;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            this.program = buildProgram();
            this.aPos = GLES20.glGetAttribLocation(this.program, "aPos");
            this.aUv = GLES20.glGetAttribLocation(this.program, "aUv");
            this.uTex = GLES20.glGetUniformLocation(this.program, "uTex");
            this.uSt = GLES20.glGetUniformLocation(this.program, "uSt");
            this.uCrop = GLES20.glGetUniformLocation(this.program, "uCrop");
            this.uBevAngles = GLES20.glGetUniformLocation(this.program, "uBevAngles");
            this.uAspect = GLES20.glGetUniformLocation(this.program, "uAspect");
            this.uWindowSize = GLES20.glGetUniformLocation(this.program, "uWindowSize");
            this.uRadius = GLES20.glGetUniformLocation(this.program, "uRadius");

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            this.oesTexId = tex[0];
            GLES20.glBindTexture(36197, this.oesTexId);
            GLES20.glTexParameteri(36197, 10241, 9729);
            GLES20.glTexParameteri(36197, 10240, 9729);
            GLES20.glTexParameteri(36197, 10242, 33071);
            GLES20.glTexParameteri(36197, 10243, 33071);

            this.panoDisplayTexture = new SurfaceTexture(this.oesTexId);
            // Fixed 1920x972 landscape buffer matching com.byd.avc SurfaceView dimensions (1280dp x 648dp @ 240dpi)
            // so libVehiclePano.so renders the full-height Top-Down BEV column on the right with zero Front/Side camera!
            this.panoDisplayTexture.setDefaultBufferSize(PANO_BUFFER_W, PANO_BUFFER_H);
            this.panoDisplayTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    if (!VehiclePanoView.this.activeOnScreen) {
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - VehiclePanoView.this.lastStandbyFrameMs < 2000L) {
                            return; // 1 frame every 2 seconds in standby (0.5 fps)
                        }
                        VehiclePanoView.this.lastStandbyFrameMs = now;
                    }
                    if (VehiclePanoView.this.glView != null) {
                        VehiclePanoView.this.glView.requestRender();
                    }
                }
            });
            this.panoDisplaySurface = new Surface(this.panoDisplayTexture);

            Log.i(TAG, "GL off-screen panoDisplaySurface created " + PANO_BUFFER_W + "x" + PANO_BUFFER_H);
            startPanoPipeline(this.panoDisplaySurface);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.viewW = Math.max(1, width);
            this.viewH = Math.max(1, height);
            GLES20.glViewport(0, 0, this.viewW, this.viewH);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (this.panoDisplayTexture != null) {
                try {
                    this.panoDisplayTexture.updateTexImage();
                    this.panoDisplayTexture.getTransformMatrix(this.stMatrix);
                } catch (Throwable ignored) {}
            }

            if (!VehiclePanoView.this.activeOnScreen) {
                return;
            }

            GLES20.glUseProgram(this.program);
            GLES20.glViewport(0, 0, this.viewW, this.viewH);

            GLES20.glUniformMatrix4fv(this.uSt, 1, false, this.stMatrix, 0);
            // Note: because stMatrix flips Y (v=0 is bottom of image, v=1 is top of image),
            // top crop (cropTop) trims from v=1 downward (1.0 - cropTop), and bottom crop (cropBottom) trims from v=0 upward (1.0 - cropBottom).
            GLES20.glUniform4f(
                this.uCrop,
                VehiclePanoView.this.cropLeft,
                1.0f - VehiclePanoView.this.cropBottom,
                VehiclePanoView.this.cropRight,
                1.0f - VehiclePanoView.this.cropTop
            );

            GLES20.glUniform4f(this.uBevAngles, VehiclePanoView.this.angleFront,
                VehiclePanoView.this.angleRear, VehiclePanoView.this.angleLeft, VehiclePanoView.this.angleRight);
            GLES20.glUniform1f(this.uAspect, (float) this.viewW / (float) this.viewH);
            GLES20.glUniform2f(this.uWindowSize, (float) this.viewW, (float) this.viewH);
            GLES20.glUniform1f(this.uRadius, VehiclePanoView.this.cornerRadiusPx);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(36197, this.oesTexId);
            GLES20.glUniform1i(this.uTex, 0);

            this.quadPos.position(0);
            GLES20.glEnableVertexAttribArray(this.aPos);
            GLES20.glVertexAttribPointer(this.aPos, 2, GLES20.GL_FLOAT, false, 0, (Buffer) this.quadPos);

            this.quadUv.position(0);
            GLES20.glEnableVertexAttribArray(this.aUv);
            GLES20.glVertexAttribPointer(this.aUv, 2, GLES20.GL_FLOAT, false, 0, (Buffer) this.quadUv);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(this.aPos);
            GLES20.glDisableVertexAttribArray(this.aUv);
        }

        private int buildProgram() {
            int vs = compile(GLES20.GL_VERTEX_SHADER, VERT);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAG);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vs);
            GLES20.glAttachShader(prog, fs);
            GLES20.glLinkProgram(prog);
            return prog;
        }

        private int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }
    }

    private void startPanoPipeline(final Surface targetSurface) {
        if (targetSurface == null || !targetSurface.isValid()) return;
        if (this.isInitializing || this.isPanoReady) return;
        this.isInitializing = true;

        final Context ctx = getContext().getApplicationContext();

        this.executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    OemPanoProfile profile = OemPanoProfile.resolve(ctx);
                    panoApi = new PanoAPI();
                    panoApi.initialize(profile.model.getAbsolutePath(), profile.width, profile.height,
                            profile.parameters.getAbsolutePath(), ctx);
                    panoApi.setCameraType(profile.cameraType, profile.cameraParameterType);
                    if (!profile.country.isEmpty()) panoApi.setCountryCode(profile.country);
                    panoApi.setAutomaticCalibrationStr(profile.intrinsic.getAbsolutePath());
                    panoApi.setCarResources(profile.car, profile.subCar, profile.color, profile.panorama.getAbsolutePath());
                    panoApi.setCarTransparent(1.0f); // 1.0f = Opaque car body in PIAPI.setTransparent()!
                    panoApi.onDrawFrameCallbakc(new PanoAPI.OnDrawFrameCallbackListener() {
                        @Override
                        public int onDrawFrameCallback(int code) {
                            return 0;
                        }
                    });

                    // 4. Bind off-screen fixed-size preview display Surface
                    panoApi.initDisplay(ctx, targetSurface);

                    // 5. Retrieve Camera SurfaceTexture for CameraFeed (camId 0)
                    cameraTexture = panoApi.getCameraSurfaceTexture();
                    if (cameraTexture != null) {
                        cameraSurface = new Surface(cameraTexture);
                        Log.i(TAG, "Camera surface created successfully from PanoAPI");
                    } else {
                        Log.e(TAG, "Failed to get camera surface texture from PanoAPI!");
                    }

                    // 6. Set initial viewpoint
                    panoApi.setViewpoint(currentViewpoint);

                    ctx.getSharedPreferences("pnp", 0).edit().remove("pano_profile_error")
                        .putBoolean("pano_profile_ready", true).apply();
                    isPanoReady = true;
                    isInitializing = false;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (readyListener != null && cameraSurface != null) {
                                readyListener.onCameraSurfaceReady(cameraSurface);
                            }
                        }
                    });

                } catch (Throwable t) {
                    Log.e(TAG, "PanoAPI initialization error: " + t, t);
                    isInitializing = false;
                    java.io.StringWriter diagnostic = new java.io.StringWriter();
                    t.printStackTrace(new java.io.PrintWriter(diagnostic));
                    ctx.getSharedPreferences("pnp", 0).edit()
                        .putBoolean("pano_profile_ready", false)
                        .putString("pano_profile_summary", "Unavailable: " + t.getClass().getSimpleName())
                        .putString("pano_profile_error", diagnostic.toString()).apply();
                    mainHandler.post(() -> {
                        if (readyListener != null) readyListener.onCameraSurfaceError();
                        android.widget.Toast.makeText(ctx, "ยังโหลดข้อมูล Top view จาก AVC ไม่สำเร็จ", android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    public Surface getCameraSurface() {
        return this.cameraSurface;
    }

    public void setViewpoint(int vp) {
        this.currentViewpoint = vp;
        if (this.prefs != null) {
            this.prefs.edit().putInt("pano_vp", vp).apply();
        }
        if (this.panoApi != null && this.isPanoReady) {
            try {
                this.panoApi.setViewpoint(vp);
                Log.i(TAG, "PanoAPI setViewpoint=" + vp);
            } catch (Throwable t) {
                Log.w(TAG, "setViewpoint failed: " + t);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

}
