package com.little_wizard.tdc.ui.draw;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.widget.Toast;

/**
 * EditActivity에 생성되는 View
 * BitmapImage 불러운 후 사진 조정 및 연산(확대, 축소, 회전)
 * EditActivity - EditImageView - BitmapDrawable - BitmapImage
 */
public class EditImageView extends androidx.appcompat.widget.AppCompatImageView {
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    Bitmap bitmap;
    BitmapDrawable bitmapDrawable;
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();
    float[] matrixValue = new float[9];
    float[] savedMatrixValue = new float[9];

    PointF start = new PointF();
    PointF mid = new PointF();
    float scale = 1f;
    float oldDist = 1f, nowDist = 0;
    double oldRadian = 0, nowRadian = 0;
    double oldDegree = 0, nowDegree = 0;
    float centerLine = 0;
    float width, height;
    float linePosX;

    private static final float MIN_ZOOM = 0.7f;
    private static final float MAX_ZOOM = 3.0f;

    public EditImageView(Context context) {
        super(context);
    }

    public void setBitmap(Bitmap bitmap, Display display) {
        Point size = new Point();
        display.getSize(size);
        centerLine = (float) size.x / 2;

        this.bitmap = getResizedBitmap(bitmap, size.x, size.y);
        bitmapDrawable = new BitmapDrawable(this.bitmap);
        setImageDrawable(bitmapDrawable);
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        invalidate();
    }

    /**
     * matrix 현재 사진 상태 행렬
     * savedMatrix 이전 사진 상태 행렬
     * start 터치 이벤트가 시작되는 Point
     * mid 멀티터치일 때 중점
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Rect bounds = getDrawable().getBounds();
        width = bounds.right - bounds.left;
        height = bounds.bottom - bounds.top;

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    oldRadian = Math.atan2(event.getY() - mid.y, event.getX() - mid.x);
                    oldDegree = (oldRadian * 180) / Math.PI;
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                } else if (mode == ZOOM) {
                    savedMatrix.getValues(savedMatrixValue);
                    matrix.getValues(matrixValue);

                    float scaleX = matrixValue[Matrix.MSCALE_X];
                    float scaleY = matrixValue[Matrix.MSCALE_Y];
                    if(scaleX > MAX_ZOOM || scaleX < MIN_ZOOM || scaleY > MAX_ZOOM || scaleY < MIN_ZOOM) {
                        mode = NONE;
                        break;
                    }

                    nowDist = spacing(event);

                    if (nowDist > 10f) {
                        matrix.set(savedMatrix);
                        scale = nowDist / oldDist;

                        nowRadian = Math.atan2(event.getY() - mid.y, event.getX() - mid.x);
                        nowDegree = (nowRadian * 180) / Math.PI;

                        matrix.postScale(scale, scale, mid.x, mid.y);
                        matrix.postRotate((float) (nowDegree - oldDegree), mid.x, mid.y);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }
        limitZoom(matrix);
        limitDrag(matrix);

        setImageMatrix(matrix);
        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    public Bitmap getResizedBitmap(Bitmap bitmap, int layoutWidth, int layoutHeight) {
        float width = bitmap.getWidth();
        float height = bitmap.getHeight();

        float newWidth = layoutWidth;
        float newHeight = layoutHeight;
        float rate = height / width;

        if (layoutHeight / layoutWidth > height / width) {
            newHeight = (int) (layoutWidth * rate);
            Log.d("rate", "1");
        } else {
            newWidth = (int) (layoutHeight / rate);
        }

        return Bitmap.createScaledBitmap(bitmap, (int) newWidth, (int) newHeight, true);
    }

    public float getRealScale(){
        matrix.getValues(matrixValue);
        float scaleX = matrixValue[Matrix.MSCALE_X];
        float skewY = matrixValue[Matrix.MSKEW_Y];
        return (float) Math.sqrt(scaleX * scaleX + skewY * skewY);
    }

    public float getRealDegree(){
        matrix.getValues(matrixValue);
        float scaleX = matrixValue[Matrix.MSCALE_X];
        float skewX = matrixValue[Matrix.MSKEW_X];
        return -Math.round(Math.atan2(skewX, scaleX) * (180 / Math.PI));
    }

    public float getRealRadians(){
        matrix.getValues(matrixValue);
        float scaleX = matrixValue[Matrix.MSCALE_X];
        float skewX = matrixValue[Matrix.MSKEW_X];
        return -(float)Math.atan2(skewX, scaleX);
    }

    private void limitZoom(Matrix m) {
        float[] values = new float[9];
        m.getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        if(scaleX > MAX_ZOOM) {
            scaleX = MAX_ZOOM;
        } else if(scaleX < MIN_ZOOM) {
            scaleX = MIN_ZOOM;
        }

        if(scaleY > MAX_ZOOM) {
            scaleY = MAX_ZOOM;
        } else if(scaleY < MIN_ZOOM) {
            scaleY = MIN_ZOOM;
        }

        values[Matrix.MSCALE_X] = scaleX;
        values[Matrix.MSCALE_Y] = scaleY;
        m.setValues(values);

    }

    /**
     *
     * @param matrix
     */
    private void limitDrag(Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];

        Rect bounds = getDrawable().getBounds();
        int viewWidth = getResources().getDisplayMetrics().widthPixels;
        int viewHeight = getResources().getDisplayMetrics().heightPixels;

        int width = bounds.right - bounds.left;
        int height = bounds.bottom - bounds.top;

        float minX = (-width + 20) * scaleX;
        float minY = (-height + 20) * scaleY;

        if(transX > (viewWidth - 20)) {
            transX = viewWidth - 20;
        } else if(transX < minX) {
            transX = minX;
        }

        if(transY > (viewHeight - 80)) {
            transY = viewHeight - 80;
        } else if(transY < minY) {
            transY = minY;
        }

        values[Matrix.MTRANS_X] = transX;
        values[Matrix.MTRANS_Y] = transY;
        matrix.setValues(values);
    }

    /**
     *
     * @return
     */
    public Bitmap getResult() {
        //기존 비트맵 이미지 좌표
        float points[][] = {{0, 0}, {bitmap.getWidth(), 0},
                {0, bitmap.getHeight()}, {bitmap.getWidth(), bitmap.getHeight()}};
        float rotaionPoints[][] = {{0, 0}, {0, 0}, {0, 0}, {0, 0}};

        float radians = getRealRadians();
        float degree = getRealDegree();

        float maxX, minX, maxY, minY;
        maxX = maxY = Float.MIN_VALUE;
        minX = minY = Float.MAX_VALUE;

        // 회전 후 비트맵 이미지 꼭짓점의 좌표
        // 0  1
        // 2  3
        for(int i=0;i<4;i++){
            rotaionPoints[i][0] = (float)Math.cos(radians) * points[i][0] - (float)Math.sin(radians) * points[i][1];
            rotaionPoints[i][1] = (float)Math.sin(radians) * points[i][0] + (float)Math.cos(radians) * points[i][1];

            maxX = Math.max(maxX, rotaionPoints[i][0]);
            minX = Math.min(minX, rotaionPoints[i][0]);
            maxY = Math.max(maxY, rotaionPoints[i][1]);
            minY = Math.min(minY, rotaionPoints[i][1]);
        }

        // 회전 후 좌표값 보정
        if(minX < 0){
            for(int i=0;i<4;i++){
                rotaionPoints[i][0] += Math.abs(minX);
            }
            maxX += Math.abs(minX);
            minX = 0;
        }
        if(minY < 0){
            for(int i=0;i<4;i++){
                rotaionPoints[i][1] += Math.abs(minY);
            }
            maxY += Math.abs(minY);
            minY = 0;
        }

        // 이미지 회전 유지하는 비트맵 생성
        float h = maxY - minY;
        float w = maxX - minX;

        Matrix m = new Matrix();
        m.postRotate(degree, bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        m.postTranslate((w - bitmap.getWidth()) / 2, (h - bitmap.getHeight()) / 2);

        // 뒷 배경 생성
        Bitmap image = Bitmap.createBitmap((int)w, (int)h, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.WHITE);

        // 기준 축 생성
        float scale = getRealScale();
        if(matrixValue[Matrix.MTRANS_X] < centerLine){
            linePosX = rotaionPoints[0][0] + (centerLine - matrixValue[Matrix.MTRANS_X]) / scale;
        }else{
            linePosX = rotaionPoints[0][0] - ((matrixValue[Matrix.MTRANS_X] - centerLine) / scale);
        }

        if(linePosX < 0 || maxX < linePosX) {
            Toast.makeText(getContext(), "다시 설정하세요.", Toast.LENGTH_LONG).show();
            return null;
        }

        Canvas canvas = new Canvas(image);
        canvas.drawBitmap(image, 0, 0, null);
        canvas.drawBitmap(bitmap, m, null);

        Paint paint = new Paint();

        paint.setColor(0x50ff0000);
        paint.setStrokeWidth(0);
        canvas.drawRect(linePosX, 0, w, h, paint);

        return image;
    }

    public float getLinePosX(){return linePosX;}
}
