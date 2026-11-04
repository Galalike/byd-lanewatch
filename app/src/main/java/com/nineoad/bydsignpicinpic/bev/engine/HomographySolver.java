package com.nineoad.bydsignpicinpic.bev.engine;

import com.nineoad.bydsignpicinpic.bev.profile.Point2D;

/**
 * Universal 4-Point Homography & Perspective Transformation Solver.
 * Computes 3x3 Homography Matrix H such that [x', y', 1]^T ~ H * [x, y, 1]^T
 * using 8-equation Direct Linear Transform (DLT) with Gaussian Elimination.
 * Runs in pure Java with 0ms overhead, completely portable to any platform.
 */
public class HomographySolver {

    public static float[] computeHomography(Point2D[] src, Point2D[] dst) {
        if (src == null || dst == null || src.length < 4 || dst.length < 4) {
            return identityMatrix();
        }

        // Build 8x8 system of linear equations A * h = b
        double[][] a = new double[8][8];
        double[] b = new double[8];

        for (int i = 0; i < 4; i++) {
            double sx = src[i].x;
            double sy = src[i].y;
            double dx = dst[i].x;
            double dy = dst[i].y;

            // Row 2*i: sx*h0 + sy*h1 + h2 - sx*dx*h6 - sy*dx*h7 = dx
            a[2 * i][0] = sx;
            a[2 * i][1] = sy;
            a[2 * i][2] = 1.0;
            a[2 * i][3] = 0.0;
            a[2 * i][4] = 0.0;
            a[2 * i][5] = 0.0;
            a[2 * i][6] = -sx * dx;
            a[2 * i][7] = -sy * dx;
            b[2 * i] = dx;

            // Row 2*i+1: sx*h3 + sy*h4 + h5 - sx*dy*h6 - sy*dy*h7 = dy
            a[2 * i + 1][0] = 0.0;
            a[2 * i + 1][1] = 0.0;
            a[2 * i + 1][2] = 0.0;
            a[2 * i + 1][3] = sx;
            a[2 * i + 1][4] = sy;
            a[2 * i + 1][5] = 1.0;
            a[2 * i + 1][6] = -sx * dy;
            a[2 * i + 1][7] = -sy * dy;
            b[2 * i + 1] = dy;
        }

        double[] h = gaussianElimination(a, b);
        if (h == null) {
            return identityMatrix();
        }

        float[] result = new float[9];
        for (int i = 0; i < 8; i++) {
            result[i] = (float) h[i];
        }
        result[8] = 1.0f; // h8 is normalized to 1.0
        return result;
    }

    public static Point2D projectPoint(float[] h, Point2D p) {
        if (h == null || h.length < 9 || p == null) return p;
        float x = p.x;
        float y = p.y;
        float w = h[6] * x + h[7] * y + h[8];
        if (Math.abs(w) < 1e-7f) w = 1e-7f;
        float px = (h[0] * x + h[1] * y + h[2]) / w;
        float py = (h[3] * x + h[4] * y + h[5]) / w;
        return new Point2D(px, py);
    }

    public static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
        };
    }

    private static double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(A[i][p]) > Math.abs(A[max][p])) {
                    max = i;
                }
            }
            double[] temp = A[p]; A[p] = A[max]; A[max] = temp;
            double t = b[p]; b[p] = b[max]; b[max] = t;

            if (Math.abs(A[p][p]) <= 1e-9) {
                return null; // Singular matrix
            }

            for (int i = p + 1; i < n; i++) {
                double alpha = A[i][p] / A[p][p];
                b[i] -= alpha * b[p];
                for (int j = p; j < n; j++) {
                    A[i][j] -= alpha * A[p][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += A[i][j] * x[j];
            }
            x[i] = (b[i] - sum) / A[i][i];
        }
        return x;
    }
}
