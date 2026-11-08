package com.nineoad.bydsignpicinpic.overlay;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FisheyeGLView extends GLSurfaceView {
    private static final String FRAG =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "varying vec2 vUv;\n" +
        "uniform samplerExternalOES uTex;\n" +
        "uniform mat4 uSt;\n" +
        "uniform vec4 uCrop;\n" +
        "uniform mat2 uRot;\n" +
        "uniform float uStrength;\n" +
        "uniform float uZoom;\n" +
        "uniform vec2 uCenter;\n" +
        "uniform float uAspect;\n" +
        "uniform vec2 uWindowSize;\n" +
        "uniform float uRadius;\n" +
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
        "  vec2 cuv = vec2(mix(uCrop.x, uCrop.z, vUv.x), mix(uCrop.y, uCrop.w, vUv.y));\n" +
        "  vec2 p = cuv - uCenter;\n" +
        "  p = uRot * p;\n" +
        "  vec2 pa = vec2(p.x * uAspect, p.y);\n" +
        "  float r2 = dot(pa, pa);\n" +
        "  float f = (1.0 + uStrength * r2) / uZoom;\n" +
        "  vec2 src = uCenter + p * f;\n" +
        "  vec2 tc = (uSt * vec4(src, 0.0, 1.0)).xy;\n" +
        "  if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) {\n" +
        "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  } else {\n" +
        "    gl_FragColor = texture2D(uTex, tc);\n" +
        "  }\n" +
        "}\n";
    private static final String TAG = "FisheyeGL";
    private static final String VERT = "attribute vec4 aPos;\nattribute vec2 aUv;\nvarying vec2 vUv;\nvoid main() { gl_Position = aPos; vUv = aUv; }\n";

    private volatile boolean isDual = false;
    private volatile int singleSide = 1; // 1 = left, 2 = right
    private volatile boolean waitingForFrame = false;
    private volatile boolean activeOnScreen = false;
    private volatile long lastFrameArrivalMs = 0L;
    private volatile long lastStandbyFrameMs = 0L;
    private volatile boolean hasWarmFrameL = false;
    private volatile boolean hasWarmFrameR = false;
    private volatile boolean hasWarmFrameRear = false;
    private volatile boolean freshFrameL = false;
    private volatile boolean freshFrameR = false;
    private volatile boolean freshFrameRear = false;
    private volatile int lastFixedW = -1;
    private volatile int lastFixedH = -1;

    private final Runnable frameTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (FisheyeGLView.this.waitingForFrame) {
                Log.d(TAG, "Frame wait timeout elapsed, releasing blank gate");
                FisheyeGLView.this.waitingForFrame = false;
                FisheyeGLView.this.requestRender();
            }
        }
    };

    public void startWaitingForFreshFrame() {
        this.waitingForFrame = true;
        this.freshFrameL = false;
        this.freshFrameR = false;
        this.freshFrameRear = false;
        removeCallbacks(this.frameTimeoutRunnable);
        // Timeout fallback: if camera HAL is delayed, release after 140ms (~3-4 frames at 25fps)
        postDelayed(this.frameTimeoutRunnable, 140L);
        requestRender(); // Immediately clears to transparent black
    }

    private void checkFreshFrameSatisfied() {
        if (!this.waitingForFrame) {
            requestRender();
            return;
        }
        boolean satisfied;
        if (this.isDual) {
            satisfied = (this.freshFrameL || this.freshFrameR);
        } else {
            switch (this.singleSide) {
                case 1:
                case 5:
                case 11:
                case 13:
                case 21:
                    satisfied = this.freshFrameL;
                    break;
                case 2:
                case 12:
                case 14:
                case 22:
                    satisfied = this.freshFrameR;
                    break;
                case 4:
                    satisfied = this.freshFrameRear;
                    break;
                default:
                    satisfied = true;
                    break;
            }
        }
        if (satisfied) {
            this.waitingForFrame = false;
            removeCallbacks(this.frameTimeoutRunnable);
            requestRender();
        }
    }

    public void setActiveOnScreen(boolean active) {
        if (this.activeOnScreen != active) {
            this.activeOnScreen = active;
            if (active) {
                startWaitingForFreshFrame();
            } else {
                this.waitingForFrame = false;
                removeCallbacks(this.frameTimeoutRunnable);
                requestRender();
            }
        } else if (active) {
            requestRender();
        }
    }

    public boolean isActiveOnScreen() {
        return this.activeOnScreen;
    }

    public long getLastFrameArrivalMs() {
        return this.lastFrameArrivalMs;
    }

    public void resetWarmState() {
        this.hasWarmFrameL = false;
        this.hasWarmFrameR = false;
        this.hasWarmFrameRear = false;
    }

    // Rear parameters
    private volatile float pRotDegRear = 0.0f;
    private volatile float pFineRear = 0.0f;
    private volatile boolean pMirrorRear = false;
    private volatile boolean pFlipVRear = false;
    private final float[] pCropRear = new float[]{0.0f, 0.0f, 1.0f, 1.0f};
    private volatile float pStrengthRear = 0.0f;
    private volatile float pZoomRear = 1.0f;
    private volatile float pCxRear = 0.5f;
    private volatile float pCyRear = 0.5f;

    // Left parameters
    private volatile float pRotDegL = 0.0f;
    private volatile float pFineL = 0.0f;
    private volatile boolean pMirrorL = false;
    private volatile boolean pFlipVL = false;
    private final float[] pCropL = new float[]{0.0f, 0.0f, 1.0f, 1.0f};
    private volatile float pStrengthL = 0.0f;
    private volatile float pZoomL = 1.0f;
    private volatile float pCxL = 0.5f;
    private volatile float pCyL = 0.5f;

    // Right parameters
    private volatile float pRotDegR = 0.0f;
    private volatile float pFineR = 0.0f;
    private volatile boolean pMirrorR = false;
    private volatile boolean pFlipVR = false;
    private final float[] pCropR = new float[]{0.0f, 0.0f, 1.0f, 1.0f};
    private volatile float pStrengthR = 0.0f;
    private volatile float pZoomR = 1.0f;
    private volatile float pCxR = 0.5f;
    private volatile float pCyR = 0.5f;

    // 4-Quadrant Wheel Monitor (FL, FR, RL, RR)
    // Index: 0=FL (Left Cam), 1=FR (Right Cam), 2=RL (Left Cam), 3=RR (Right Cam)
    private final float[] pRotQuad = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] pFineQuad = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private final boolean[] pMirQuad = new boolean[]{false, false, false, false};
    private final boolean[] pFlipVQuad = new boolean[]{false, false, false, false};
    private final float[][] pCropQuad = new float[][]{
            new float[]{0.0f, 0.0f, 1.0f, 0.55f}, // FL: upper half of left camera
            new float[]{0.0f, 0.0f, 1.0f, 0.55f}, // FR: upper half of right camera
            new float[]{0.0f, 0.45f, 1.0f, 1.0f}, // RL: lower half of left camera
            new float[]{0.0f, 0.45f, 1.0f, 1.0f}  // RR: lower half of right camera
    };
    private final float[] pStrengthQuad = new float[]{-0.15f, -0.15f, -0.15f, -0.15f};
    private final float[] pZoomQuad = new float[]{1.25f, 1.25f, 1.25f, 1.25f};
    private final float[] pCxQuad = new float[]{0.5f, 0.5f, 0.5f, 0.5f};
    private final float[] pCyQuad = new float[]{0.35f, 0.35f, 0.65f, 0.65f};

    public void setParamsQuadWheel(int wheelIndex, float rot, float fine, boolean mir, boolean flipV,
                                   float[] crop, float str, float zoom, float cx, float cy) {
        if (wheelIndex >= 0 && wheelIndex < 4) {
            this.pRotQuad[wheelIndex] = rot;
            this.pFineQuad[wheelIndex] = fine;
            this.pMirQuad[wheelIndex] = mir;
            this.pFlipVQuad[wheelIndex] = flipV;
            if (crop != null && crop.length == 4) {
                System.arraycopy(crop, 0, this.pCropQuad[wheelIndex], 0, 4);
            }
            this.pStrengthQuad[wheelIndex] = str;
            this.pZoomQuad[wheelIndex] = zoom;
            this.pCxQuad[wheelIndex] = cx;
            this.pCyQuad[wheelIndex] = cy;
            requestRender();
        }
    }

    private volatile float pAspect = 1.7777778f;
    private volatile float cornerRadiusPx = 0.0f;

    public void setCornerRadiusPx(float radiusPx) {
        this.cornerRadiusPx = radiusPx;
        requestRender();
    }
    private SurfaceReadyListener readyListener;
    private Renderer renderer;

    public interface SurfaceReadyListener {
        void onReady(Surface surface);
    }

    public FisheyeGLView(Context context) {
        super(context);
        init();
    }

    public FisheyeGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setZOrderOnTop(false);
        setZOrderMediaOverlay(true);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setPreserveEGLContextOnPause(true);
        Renderer renderer = new Renderer();
        this.renderer = renderer;
        setRenderer(renderer);
        setRenderMode(0);
    }

    public void setSurfaceReadyListener(SurfaceReadyListener surfaceReadyListener) {
        this.readyListener = surfaceReadyListener;
    }

    public void setRenderSize(final int i, final int i2) {
        if (i == this.lastFixedW && i2 == this.lastFixedH) {
            return;
        }
        this.lastFixedW = i;
        this.lastFixedH = i2;
        post(new Runnable() {
            @Override
            public final void run() {
                try {
                    FisheyeGLView.this.getHolder().setFixedSize(i, i2);
                } catch (Throwable unused) {
                }
            }
        });
    }

    public Surface getCameraSurface() {
        return this.renderer != null ? this.renderer.camSurfaceL : null;
    }

    public Surface getCameraSurfaceLeft() {
        return this.renderer != null ? this.renderer.camSurfaceL : null;
    }

    public Surface getCameraSurfaceRight() {
        return this.renderer != null ? this.renderer.camSurfaceR : null;
    }

    public Surface getCameraSurfaceRear() {
        return this.renderer != null ? this.renderer.camSurfaceRear : null;
    }

    public void setParamsRear(float f, float f2, boolean z, boolean z2, float[] fArr, float f3, float f4, float f5, float f6) {
        this.pRotDegRear = f;
        this.pFineRear = f2;
        this.pMirrorRear = z;
        this.pFlipVRear = z2;
        if (fArr != null && fArr.length == 4) {
            this.pCropRear[0] = fArr[0];
            this.pCropRear[1] = fArr[1];
            this.pCropRear[2] = fArr[2];
            this.pCropRear[3] = fArr[3];
        }
        this.pStrengthRear = f3;
        this.pZoomRear = f4;
        this.pCxRear = f5;
        this.pCyRear = f6;
        requestRender();
    }

    public void setDualMode(boolean dual, int side) {
        boolean modeChanged = (this.isDual != dual || this.singleSide != side);
        this.isDual = dual;
        this.singleSide = side;
        if (modeChanged) {
            startWaitingForFreshFrame();
        } else if (!this.waitingForFrame) {
            requestRender();
        }
    }

    public void setParamsLeft(float f, float f2, boolean z, boolean z2, float[] fArr, float f3, float f4, float f5, float f6) {
        this.pRotDegL = f;
        this.pFineL = f2;
        this.pMirrorL = z;
        this.pFlipVL = z2;
        if (fArr != null && fArr.length == 4) {
            this.pCropL[0] = fArr[0];
            this.pCropL[1] = fArr[1];
            this.pCropL[2] = fArr[2];
            this.pCropL[3] = fArr[3];
        }
        this.pStrengthL = f3;
        this.pZoomL = f4;
        this.pCxL = f5;
        this.pCyL = f6;
        requestRender();
    }

    public void setParamsRight(float f, float f2, boolean z, boolean z2, float[] fArr, float f3, float f4, float f5, float f6) {
        this.pRotDegR = f;
        this.pFineR = f2;
        this.pMirrorR = z;
        this.pFlipVR = z2;
        if (fArr != null && fArr.length == 4) {
            this.pCropR[0] = fArr[0];
            this.pCropR[1] = fArr[1];
            this.pCropR[2] = fArr[2];
            this.pCropR[3] = fArr[3];
        }
        this.pStrengthR = f3;
        this.pZoomR = f4;
        this.pCxR = f5;
        this.pCyR = f6;
        requestRender();
    }

    public void setParams(float f, float f2, boolean z, boolean z2, float[] fArr, float f3, float f4, float f5, float f6) {
        if (this.singleSide == 4) {
            setParamsRear(f, f2, z, z2, fArr, f3, f4, f5, f6);
        } else if (this.singleSide == 2) {
            setParamsRight(f, f2, z, z2, fArr, f3, f4, f5, f6);
        } else {
            setParamsLeft(f, f2, z, z2, fArr, f3, f4, f5, f6);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static FloatBuffer fb(float[] fArr) {
        FloatBuffer floatBufferAsFloatBuffer = ByteBuffer.allocateDirect(fArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        floatBufferAsFloatBuffer.put(fArr).position(0);
        return floatBufferAsFloatBuffer;
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class Renderer implements GLSurfaceView.Renderer {
        private int aPos;
        private int aUv;
        private SurfaceTexture camSTL;
        private SurfaceTexture camSTR;
        private SurfaceTexture camSTRear;
        volatile Surface camSurfaceL;
        volatile Surface camSurfaceR;
        volatile Surface camSurfaceRear;
        private int oesTexL;
        private int oesTexR;
        private int oesTexRear;
        private int program;
        private final FloatBuffer quadPos;
        private final FloatBuffer quadUv;
        private final float[] rot;
        private final float[] stMatrixL;
        private final float[] stMatrixR;
        private final float[] stMatrixRear;
        private int uAspect;
        private int uWindowSize;
        private int uRadius;
        private int uCenter;
        private int uCrop;
        private int uRot;
        private int uSt;
        private int uStrength;
        private int uTex;
        private int uZoom;
        private int viewW;
        private int viewH;

        private Renderer() {
            this.stMatrixL = new float[16];
            this.stMatrixR = new float[16];
            this.stMatrixRear = new float[16];
            android.opengl.Matrix.setIdentityM(this.stMatrixL, 0);
            android.opengl.Matrix.setIdentityM(this.stMatrixR, 0);
            android.opengl.Matrix.setIdentityM(this.stMatrixRear, 0);
            this.rot = new float[4];
            this.quadPos = FisheyeGLView.fb(new float[]{-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f});
            this.quadUv = FisheyeGLView.fb(new float[]{0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f});
        }

        private void initTex(int texId) {
            GLES20.glBindTexture(36197, texId);
            GLES20.glTexParameteri(36197, 10241, 9729);
            GLES20.glTexParameteri(36197, 10240, 9729);
            GLES20.glTexParameteri(36197, 10242, 33071);
            GLES20.glTexParameteri(36197, 10243, 33071);
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
            int iBuildProgram = buildProgram();
            this.program = iBuildProgram;
            this.aPos = GLES20.glGetAttribLocation(iBuildProgram, "aPos");
            this.aUv = GLES20.glGetAttribLocation(this.program, "aUv");
            this.uTex = GLES20.glGetUniformLocation(this.program, "uTex");
            this.uSt = GLES20.glGetUniformLocation(this.program, "uSt");
            this.uCrop = GLES20.glGetUniformLocation(this.program, "uCrop");
            this.uRot = GLES20.glGetUniformLocation(this.program, "uRot");
            this.uStrength = GLES20.glGetUniformLocation(this.program, "uStrength");
            this.uZoom = GLES20.glGetUniformLocation(this.program, "uZoom");
            this.uCenter = GLES20.glGetUniformLocation(this.program, "uCenter");
            this.uAspect = GLES20.glGetUniformLocation(this.program, "uAspect");
            this.uWindowSize = GLES20.glGetUniformLocation(this.program, "uWindowSize");
            this.uRadius = GLES20.glGetUniformLocation(this.program, "uRadius");

            int[] iArr = new int[3];
            GLES20.glGenTextures(3, iArr, 0);
            this.oesTexL = iArr[0];
            this.oesTexR = iArr[1];
            this.oesTexRear = iArr[2];
            initTex(this.oesTexL);
            initTex(this.oesTexR);
            initTex(this.oesTexRear);

            this.camSTL = new SurfaceTexture(this.oesTexL);
            this.camSTL.setDefaultBufferSize(1920, 1080);
            this.camSTL.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (!FisheyeGLView.this.activeOnScreen) {
                        if (now - FisheyeGLView.this.lastStandbyFrameMs < 1000L) {
                            return; // 1 frame every 1 second in standby (1.0 fps)
                        }
                        FisheyeGLView.this.lastStandbyFrameMs = now;
                    }
                    FisheyeGLView.this.lastFrameArrivalMs = now;
                    FisheyeGLView.this.hasWarmFrameL = true;
                    FisheyeGLView.this.freshFrameL = true;
                    FisheyeGLView.this.checkFreshFrameSatisfied();
                }
            });
            this.camSurfaceL = new Surface(this.camSTL);

            this.camSTR = new SurfaceTexture(this.oesTexR);
            this.camSTR.setDefaultBufferSize(1920, 1080);
            this.camSTR.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (!FisheyeGLView.this.activeOnScreen) {
                        if (now - FisheyeGLView.this.lastStandbyFrameMs < 1000L) {
                            return; // 1 frame every 1 second in standby (1.0 fps)
                        }
                        FisheyeGLView.this.lastStandbyFrameMs = now;
                    }
                    FisheyeGLView.this.lastFrameArrivalMs = now;
                    FisheyeGLView.this.hasWarmFrameR = true;
                    FisheyeGLView.this.freshFrameR = true;
                    FisheyeGLView.this.checkFreshFrameSatisfied();
                }
            });
            this.camSurfaceR = new Surface(this.camSTR);

            this.camSTRear = new SurfaceTexture(this.oesTexRear);
            this.camSTRear.setDefaultBufferSize(1920, 1080);
            this.camSTRear.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (!FisheyeGLView.this.activeOnScreen) {
                        if (now - FisheyeGLView.this.lastStandbyFrameMs < 1000L) {
                            return; // 1 frame every 1 second in standby (1.0 fps)
                        }
                        FisheyeGLView.this.lastStandbyFrameMs = now;
                    }
                    FisheyeGLView.this.lastFrameArrivalMs = now;
                    FisheyeGLView.this.hasWarmFrameRear = true;
                    FisheyeGLView.this.freshFrameRear = true;
                    FisheyeGLView.this.checkFreshFrameSatisfied();
                }
            });
            this.camSurfaceRear = new Surface(this.camSTRear);

            Log.i(FisheyeGLView.TAG, "GL ready, dual camera surfaces created");
            if (FisheyeGLView.this.readyListener != null) {
                final Surface surface = this.camSurfaceL;
                FisheyeGLView.this.post(new Runnable() {
                    @Override
                    public void run() {
                        if (FisheyeGLView.this.readyListener != null) {
                            FisheyeGLView.this.readyListener.onReady(surface);
                        }
                    }
                });
            }
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceChanged(GL10 gl10, int i, int i2) {
            this.viewW = i;
            this.viewH = i2;
            GLES20.glViewport(0, 0, i, i2);
            if (i2 > 0) {
                if (FisheyeGLView.this.isDual) {
                    FisheyeGLView.this.pAspect = (float) (i / 2) / (float) i2;
                } else {
                    FisheyeGLView.this.pAspect = (float) i / (float) i2;
                }
            }
        }

        private void drawQuad(int tex, float[] stMat, float rotDeg, float fine, boolean mirror, boolean flipV, float[] crop, float strength, float zoom, float cx, float cy, float aspect) {
            double radians = Math.toRadians(rotDeg + fine);
            float fCos = (float) Math.cos(radians);
            float fSin = (float) Math.sin(radians);
            float f = mirror ? -1.0f : 1.0f;
            float f2 = flipV ? -1.0f : 1.0f;
            float[] fArr = this.rot;
            fArr[0] = fCos * f;
            fArr[1] = f * fSin;
            fArr[2] = (-fSin) * f2;
            fArr[3] = fCos * f2;
            GLES20.glUniformMatrix2fv(this.uRot, 1, false, fArr, 0);
            GLES20.glUniformMatrix4fv(this.uSt, 1, false, stMat, 0);
            GLES20.glUniform4f(this.uCrop, crop[0], crop[1], crop[2], crop[3]);
            GLES20.glUniform1f(this.uStrength, strength);
            GLES20.glUniform1f(this.uZoom, Math.max(0.05f, zoom));
            GLES20.glUniform2f(this.uCenter, cx, cy);
            GLES20.glUniform1f(this.uAspect, aspect);
            float viewWidthOnScreen = FisheyeGLView.this.getWidth();
            float scale = 1.0f;
            if (viewWidthOnScreen > 0.0f && this.viewW > 0) {
                scale = (float) this.viewW / viewWidthOnScreen;
            }
            float bufferRadius = FisheyeGLView.this.cornerRadiusPx * scale;
            GLES20.glUniform2f(this.uWindowSize, (float) this.viewW, (float) this.viewH);
            GLES20.glUniform1f(this.uRadius, bufferRadius);
            GLES20.glActiveTexture(33984);
            GLES20.glBindTexture(36197, tex);
            GLES20.glUniform1i(this.uTex, 0);
            this.quadPos.position(0);
            GLES20.glEnableVertexAttribArray(this.aPos);
            GLES20.glVertexAttribPointer(this.aPos, 2, 5126, false, 0, (Buffer) this.quadPos);
            this.quadUv.position(0);
            GLES20.glEnableVertexAttribArray(this.aUv);
            GLES20.glVertexAttribPointer(this.aUv, 2, 5126, false, 0, (Buffer) this.quadUv);
            GLES20.glDrawArrays(5, 0, 4);
            GLES20.glDisableVertexAttribArray(this.aPos);
            GLES20.glDisableVertexAttribArray(this.aUv);
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onDrawFrame(GL10 gl10) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(16384);

            if (!FisheyeGLView.this.activeOnScreen) {
                // Warm standby: drain incoming 1-fps buffers so OES textures stay warm and HAL BufferQueue never fills,
                // while skipping the fragment shader pass (Overdrive PASS 1C visibility gate pattern).
                if (this.camSTL != null) {
                    try {
                        this.camSTL.updateTexImage();
                        this.camSTL.getTransformMatrix(this.stMatrixL);
                    } catch (Throwable unused) {}
                }
                if (this.camSTR != null) {
                    try {
                        this.camSTR.updateTexImage();
                        this.camSTR.getTransformMatrix(this.stMatrixR);
                    } catch (Throwable unused) {}
                }
                if (this.camSTRear != null) {
                    try {
                        this.camSTRear.updateTexImage();
                        this.camSTRear.getTransformMatrix(this.stMatrixRear);
                    } catch (Throwable unused) {}
                }
                return;
            }

            if (FisheyeGLView.this.waitingForFrame) {
                return;
            }

            GLES20.glUseProgram(this.program);

            if (!FisheyeGLView.this.isDual) {
                if (FisheyeGLView.this.singleSide == 21) {
                    // COMBINED: Turn Signal Left (16:9 on Left) + Front-Left Wheel (Vertical on Right)
                    if (this.camSTL != null) {
                        try {
                            this.camSTL.updateTexImage();
                            this.camSTL.getTransformMatrix(this.stMatrixL);
                        } catch (Throwable unused) {}
                    }
                    int turnW = Math.max(1, Math.round(this.viewH * (16.0f / 9.0f)));
                    if (turnW >= this.viewW) {
                        turnW = Math.round(this.viewW * 0.72f);
                    }
                    int wheelW = Math.max(1, this.viewW - turnW);

                    // 1. Main Turn Signal Camera (16:9)
                    GLES20.glViewport(0, 0, turnW, this.viewH);
                    float aspectTurn = (float) turnW / (float) this.viewH;
                    drawQuad(this.oesTexL, this.stMatrixL,
                            FisheyeGLView.this.pRotDegL, FisheyeGLView.this.pFineL,
                            FisheyeGLView.this.pMirrorL, FisheyeGLView.this.pFlipVL,
                            FisheyeGLView.this.pCropL, FisheyeGLView.this.pStrengthL,
                            FisheyeGLView.this.pZoomL, FisheyeGLView.this.pCxL,
                            FisheyeGLView.this.pCyL, aspectTurn);

                    // 2. Attached Vertical Front-Left Wheel View
                    GLES20.glViewport(turnW, 0, wheelW, this.viewH);
                    float aspectWheel = (float) wheelW / (float) this.viewH;
                    drawQuad(this.oesTexL, this.stMatrixL,
                            FisheyeGLView.this.pRotQuad[0], FisheyeGLView.this.pFineQuad[0],
                            FisheyeGLView.this.pMirQuad[0], FisheyeGLView.this.pFlipVQuad[0],
                            FisheyeGLView.this.pCropQuad[0], FisheyeGLView.this.pStrengthQuad[0],
                            FisheyeGLView.this.pZoomQuad[0], FisheyeGLView.this.pCxQuad[0],
                            FisheyeGLView.this.pCyQuad[0], aspectWheel);
                } else if (FisheyeGLView.this.singleSide == 22) {
                    // COMBINED: Turn Signal Right (16:9 on Left) + Front-Right Wheel (Vertical on Right)
                    if (this.camSTR != null) {
                        try {
                            this.camSTR.updateTexImage();
                            this.camSTR.getTransformMatrix(this.stMatrixR);
                        } catch (Throwable unused) {}
                    }
                    int turnW = Math.max(1, Math.round(this.viewH * (16.0f / 9.0f)));
                    if (turnW >= this.viewW) {
                        turnW = Math.round(this.viewW * 0.72f);
                    }
                    int wheelW = Math.max(1, this.viewW - turnW);

                    // 1. Main Turn Signal Camera (16:9)
                    GLES20.glViewport(0, 0, turnW, this.viewH);
                    float aspectTurn = (float) turnW / (float) this.viewH;
                    drawQuad(this.oesTexR, this.stMatrixR,
                            FisheyeGLView.this.pRotDegR, FisheyeGLView.this.pFineR,
                            FisheyeGLView.this.pMirrorR, FisheyeGLView.this.pFlipVR,
                            FisheyeGLView.this.pCropR, FisheyeGLView.this.pStrengthR,
                            FisheyeGLView.this.pZoomR, FisheyeGLView.this.pCxR,
                            FisheyeGLView.this.pCyR, aspectTurn);

                    // 2. Attached Vertical Front-Right Wheel View
                    GLES20.glViewport(turnW, 0, wheelW, this.viewH);
                    float aspectWheel = (float) wheelW / (float) this.viewH;
                    drawQuad(this.oesTexR, this.stMatrixR,
                            FisheyeGLView.this.pRotQuad[1], FisheyeGLView.this.pFineQuad[1],
                            FisheyeGLView.this.pMirQuad[1], FisheyeGLView.this.pFlipVQuad[1],
                            FisheyeGLView.this.pCropQuad[1], FisheyeGLView.this.pStrengthQuad[1],
                            FisheyeGLView.this.pZoomQuad[1], FisheyeGLView.this.pCxQuad[1],
                            FisheyeGLView.this.pCyQuad[1], aspectWheel);
                } else if (FisheyeGLView.this.singleSide == 5 || FisheyeGLView.this.singleSide == 11) {
                    // FL (Front-Left Wheel - standalone)
                    if (this.camSTL != null) {
                        try {
                            this.camSTL.updateTexImage();
                            this.camSTL.getTransformMatrix(this.stMatrixL);
                        } catch (Throwable unused) {}
                    }
                    GLES20.glViewport(0, 0, this.viewW, this.viewH);
                    float aspectSingle = (this.viewH > 0) ? ((float) this.viewW / (float) this.viewH) : (16.0f / 9.0f);
                    drawQuad(this.oesTexL, this.stMatrixL,
                            FisheyeGLView.this.pRotQuad[0], FisheyeGLView.this.pFineQuad[0],
                            FisheyeGLView.this.pMirQuad[0], FisheyeGLView.this.pFlipVQuad[0],
                            FisheyeGLView.this.pCropQuad[0], FisheyeGLView.this.pStrengthQuad[0],
                            FisheyeGLView.this.pZoomQuad[0], FisheyeGLView.this.pCxQuad[0],
                            FisheyeGLView.this.pCyQuad[0], aspectSingle);
                } else if (FisheyeGLView.this.singleSide == 12) {
                    // FR (Front-Right Wheel - standalone)
                    if (this.camSTR != null) {
                        try {
                            this.camSTR.updateTexImage();
                            this.camSTR.getTransformMatrix(this.stMatrixR);
                        } catch (Throwable unused) {}
                    }
                    GLES20.glViewport(0, 0, this.viewW, this.viewH);
                    float aspectSingle = (this.viewH > 0) ? ((float) this.viewW / (float) this.viewH) : (16.0f / 9.0f);
                    drawQuad(this.oesTexR, this.stMatrixR,
                            FisheyeGLView.this.pRotQuad[1], FisheyeGLView.this.pFineQuad[1],
                            FisheyeGLView.this.pMirQuad[1], FisheyeGLView.this.pFlipVQuad[1],
                            FisheyeGLView.this.pCropQuad[1], FisheyeGLView.this.pStrengthQuad[1],
                            FisheyeGLView.this.pZoomQuad[1], FisheyeGLView.this.pCxQuad[1],
                            FisheyeGLView.this.pCyQuad[1], aspectSingle);
                } else if (FisheyeGLView.this.singleSide == 4) {
                    if (this.camSTRear != null) {
                        try {
                            this.camSTRear.updateTexImage();
                            this.camSTRear.getTransformMatrix(this.stMatrixRear);
                        } catch (Throwable unused) {}
                    }
                    GLES20.glViewport(0, 0, this.viewW, this.viewH);
                    float aspectSingle = (this.viewH > 0) ? ((float) this.viewW / (float) this.viewH) : (16.0f / 9.0f);
                    drawQuad(this.oesTexRear, this.stMatrixRear,
                            FisheyeGLView.this.pRotDegRear, FisheyeGLView.this.pFineRear,
                            FisheyeGLView.this.pMirrorRear, FisheyeGLView.this.pFlipVRear,
                            FisheyeGLView.this.pCropRear, FisheyeGLView.this.pStrengthRear,
                            FisheyeGLView.this.pZoomRear, FisheyeGLView.this.pCxRear,
                            FisheyeGLView.this.pCyRear, aspectSingle);
                } else if (FisheyeGLView.this.singleSide == 2) {
                    if (this.camSTR != null) {
                        try {
                            this.camSTR.updateTexImage();
                            this.camSTR.getTransformMatrix(this.stMatrixR);
                        } catch (Throwable unused) {}
                    }
                    GLES20.glViewport(0, 0, this.viewW, this.viewH);
                    float aspectSingle = (this.viewH > 0) ? ((float) this.viewW / (float) this.viewH) : (16.0f / 9.0f);
                    drawQuad(this.oesTexR, this.stMatrixR,
                            FisheyeGLView.this.pRotDegR, FisheyeGLView.this.pFineR,
                            FisheyeGLView.this.pMirrorR, FisheyeGLView.this.pFlipVR,
                            FisheyeGLView.this.pCropR, FisheyeGLView.this.pStrengthR,
                            FisheyeGLView.this.pZoomR, FisheyeGLView.this.pCxR,
                            FisheyeGLView.this.pCyR, aspectSingle);
                } else {
                    if (this.camSTL != null) {
                        try {
                            this.camSTL.updateTexImage();
                            this.camSTL.getTransformMatrix(this.stMatrixL);
                        } catch (Throwable unused) {}
                    }
                    GLES20.glViewport(0, 0, this.viewW, this.viewH);
                    float aspectSingle = (this.viewH > 0) ? ((float) this.viewW / (float) this.viewH) : (16.0f / 9.0f);
                    drawQuad(this.oesTexL, this.stMatrixL,
                            FisheyeGLView.this.pRotDegL, FisheyeGLView.this.pFineL,
                            FisheyeGLView.this.pMirrorL, FisheyeGLView.this.pFlipVL,
                            FisheyeGLView.this.pCropL, FisheyeGLView.this.pStrengthL,
                            FisheyeGLView.this.pZoomL, FisheyeGLView.this.pCxL,
                            FisheyeGLView.this.pCyL, aspectSingle);
                }
            } else {
                // DUAL CAMERA VIEW (Side-by-side)
                int halfW = this.viewW / 2;

                // 1. Draw LEFT camera on left half (with 1px gap)
                int leftW = Math.max(1, halfW - 1);
                if (this.camSTL != null) {
                    try {
                        this.camSTL.updateTexImage();
                        this.camSTL.getTransformMatrix(this.stMatrixL);
                    } catch (Throwable unused) {}
                }
                GLES20.glViewport(0, 0, leftW, this.viewH);
                float aspectDualLeft = (this.viewH > 0) ? ((float) leftW / (float) this.viewH) : (16.0f / 9.0f);
                drawQuad(this.oesTexL, this.stMatrixL,
                        FisheyeGLView.this.pRotDegL, FisheyeGLView.this.pFineL,
                        FisheyeGLView.this.pMirrorL, FisheyeGLView.this.pFlipVL,
                        FisheyeGLView.this.pCropL, FisheyeGLView.this.pStrengthL,
                        FisheyeGLView.this.pZoomL, FisheyeGLView.this.pCxL,
                        FisheyeGLView.this.pCyL, aspectDualLeft);

                // 2. Draw RIGHT camera on right half (with 1px gap)
                int rightX = halfW + 1;
                int rightW = Math.max(1, this.viewW - rightX);
                if (this.camSTR != null) {
                    try {
                        this.camSTR.updateTexImage();
                        this.camSTR.getTransformMatrix(this.stMatrixR);
                    } catch (Throwable unused) {}
                }
                GLES20.glViewport(rightX, 0, rightW, this.viewH);
                float aspectDualRight = (this.viewH > 0) ? ((float) rightW / (float) this.viewH) : (16.0f / 9.0f);
                drawQuad(this.oesTexR, this.stMatrixR,
                        FisheyeGLView.this.pRotDegR, FisheyeGLView.this.pFineR,
                        FisheyeGLView.this.pMirrorR, FisheyeGLView.this.pFlipVR,
                        FisheyeGLView.this.pCropR, FisheyeGLView.this.pStrengthR,
                        FisheyeGLView.this.pZoomR, FisheyeGLView.this.pCxR,
                        FisheyeGLView.this.pCyR, aspectDualRight);
            }
        }

        private int buildProgram() {
            int iCompile = compile(35633, FisheyeGLView.VERT);
            int iCompile2 = compile(35632, FisheyeGLView.FRAG);
            int iGlCreateProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(iGlCreateProgram, iCompile);
            GLES20.glAttachShader(iGlCreateProgram, iCompile2);
            GLES20.glLinkProgram(iGlCreateProgram);
            int[] iArr = new int[1];
            GLES20.glGetProgramiv(iGlCreateProgram, 35714, iArr, 0);
            if (iArr[0] == 0) {
                Log.e(FisheyeGLView.TAG, "link: " + GLES20.glGetProgramInfoLog(iGlCreateProgram));
            }
            return iGlCreateProgram;
        }

        private int compile(int i, String str) {
            int iGlCreateShader = GLES20.glCreateShader(i);
            GLES20.glShaderSource(iGlCreateShader, str);
            GLES20.glCompileShader(iGlCreateShader);
            int[] iArr = new int[1];
            GLES20.glGetShaderiv(iGlCreateShader, 35713, iArr, 0);
            if (iArr[0] == 0) {
                Log.e(FisheyeGLView.TAG, "compile: " + GLES20.glGetShaderInfoLog(iGlCreateShader));
            }
            return iGlCreateShader;
        }
    }
}
