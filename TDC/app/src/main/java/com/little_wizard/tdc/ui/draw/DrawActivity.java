package com.little_wizard.tdc.ui.draw;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.little_wizard.tdc.R;
import com.little_wizard.tdc.util.NetworkStatus;
import com.little_wizard.tdc.util.S3Transfer;
import com.little_wizard.tdc.util.draw.Coordinates;
import com.little_wizard.tdc.util.draw.ObjectBuffer;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SYMMETRY모드: EditActivity에서 축 표시 된 이미지 가져와 사용
 * ASYMMETRY, FLAT모드: CameraActivity에서 선택or촬영한 이미지 가져와 사용
 *
 * DrawActivity
 * DrawImageView - 이미지에서 모델링 할 물체의 좌표값을 구함
 * ObjectBuffer - 한 개의 이미지에서 분할 모델링 할 좌표 리스트들 저장
 * resultList:List - 이거 굳이 필요없어보이는데???????????????????????????????????
 * S3Transfer - 구한 좌표값을 AWS 서버로 전송
 */
public class DrawActivity extends AppCompatActivity implements S3Transfer.TransferCallback, DrawAdapter.ItemClickListener {
    public static final int ASYMMETRY = 1;
    public static final int SYMMETRY = 2;
    public static final int FLAT = 3;

    private DrawImageView imageView;
    Bitmap bitmap = null;
    private boolean isDrawMode = false;
    Display display;
    private int viewHeight, viewWidth;
    private float line;

    DrawAdapter adapter;
    private ObjectBuffer objectBuffer;
    List resultList;
    String filepath, filename, axis;

    S3Transfer transfer;
    ProgressDialog progressDialog;
    AlertDialog dialog;

    private AlertDialog alertDialog;
    private BackgroundThread backgroundThread;

    /**
     * AppCompatActivity
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        viewHeight = size.y;
        viewWidth = size.x;

        transfer = new S3Transfer(this);
        transfer.setCallback(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.uploading));
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.R.style.Widget_ProgressBar_Horizontal);

        Intent intent = getIntent();

        if (intent != null) {
            Uri uri = intent.getData();
            String mode = intent.getStringExtra("MODE");
            switch(mode) {
                case "ASYMMETRY":
                    //비대칭일 때 갤러리 사진 Uri 가져옴
                    imageView = new DrawImageView(this, ASYMMETRY);
                    break;
                case "SYMMETRY":
                    // 대칭일 때 byte array 가져옴, 축 설정
                    imageView = new DrawImageView(this, SYMMETRY);
                    break;
                case "FLAT":
                    imageView = new DrawImageView(this, FLAT);
                    break;
            }
            
            try {
                // 선택한 이미지 uri 가져옴
                ImageDecoder.Source source = ImageDecoder.createSource(getApplicationContext().getContentResolver(), uri);
                bitmap = ImageDecoder.decodeBitmap(source);
                bitmap = resizeBitmapImage(bitmap);
                line = intent.getFloatExtra("LINE", Float.MIN_VALUE);
                imageView.setBackgroundBitmap(bitmap);
                imageView.setLine(line);
            } catch (IOException e) {
                e.printStackTrace();
            }

            filepath = getExternalCacheDir() + "/";
            filename = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
            objectBuffer = new ObjectBuffer(filepath, filename, bitmap);
            objectBuffer.setMode(mode);
        }

        //레이아웃 설정
        setContentView(R.layout.activity_draw);
        LinearLayout layout = findViewById(R.id.layout_draw);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        );
        layout.addView(imageView, params);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().show();
        layout.invalidate();
    }

    /**
     * AppCompatActivity
     * @param menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_draw, menu);
        imageView.setMenu(menu);
        imageView.setUnClearMenu();
        imageView.clear();
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * AppCompatActivity
     * 
     * R.id.draw_undo 좌표 수정 중 한개의 작업 취소
     * R.id.draw_mode drag <-> edit 모드 변경
     * R.id.draw_add 물체 모델링 추가
     * R.id.draw_reset 좌표 수정 중 전체 작업 취소
     * R.id.draw_confirmation 좌표 구한 물체 ObjectBuffer에 추가
     * R.id.draw_save 작업 한 분할된 모델들 리스트를 확인
     *
     * @param item
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.draw_undo:
                imageView.undo();
                return true;

            case R.id.draw_mode: // drag <-> edit
                if (!isDrawMode) {
                    item.setIcon(R.drawable.ic_pan);
                } else {
                    item.setIcon(R.drawable.ic_edit);
                }
                isDrawMode = !isDrawMode;
                imageView.setItemMode(isDrawMode);
                return true;

            case R.id.draw_add:
            case R.id.draw_reset:
                imageView.clear();
                return true;

            case R.id.draw_confirmation: // 좌표 구한 물체 ObjectBuffer에 추가
                imageView.setConfirmation(true);
                if (!objectBuffer.getMode().equals("SYMMETRY")) {
                    selectAxis();
                    return true;
                }

                Bitmap bitmap = imageView.getCroppedImage().copy(Bitmap.Config.ARGB_8888, true);
                resultList = imageView.getSymmetryResult();
                if (resultList != null) {
                    List newList = new ArrayList<Coordinates>(resultList);
                    objectBuffer.push(bitmap, newList, "");
                    Toast.makeText(this, "추가 완료", Toast.LENGTH_SHORT).show();
                }
                return true;

            case R.id.draw_save: //편집한 내역들 확인, 리스트로 표시
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                RecyclerView recycler = new RecyclerView(this);
                adapter = new DrawAdapter(this);
                adapter.setClickListener(this);

                recycler.setLayoutManager(new LinearLayoutManager(this));
                recycler.setAdapter(adapter);
                adapter.setElementList(objectBuffer.getBuffer());
                builder.setView(recycler);
                builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {

                });
                builder.setPositiveButton(R.string.upload, (dialogInterface, i) -> {
                    upload(objectBuffer.getName());
                });
                dialog = builder.create();
                dialog.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * S3Transfer
     * @param state
     */
    @Override
    public void onStateChanged(TransferState state) {
        switch (state) {
            case IN_PROGRESS:
                progressDialog.show();
                break;
            case COMPLETED:
            case FAILED:
                String text = getString(state == TransferState.COMPLETED ?
                        R.string.transfer_completed : R.string.transfer_failed);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                finish();
                break;
        }
    }

    /**
     * S3Transfer
     * @param id
     * @param e
     */
    @Override
    public void onError(int id, Exception e) {
        e.printStackTrace();
    }

    /**
     * DrawAdapter
     * @param pos
     */
    @Override
    public void onItemClick(int pos) {
        objectBuffer.remove(pos);
        List<ObjectBuffer.Element> buffer = objectBuffer.getBuffer();
        adapter.setElementList(buffer);
        if (buffer.isEmpty()) {
            dialog.dismiss();
            imageView.clear();
        }
    }

    /**
     * 이미지 로드할 때 핸드폰 화면 가로 or 세로에 맞춤
     * @param source
     * @return
     */
    public Bitmap resizeBitmapImage(Bitmap source) {
        float width = source.getWidth();
        float height = source.getHeight();
        float newWidth = viewWidth;
        float newHeight = viewHeight;
        float rate = height / width;

        if (viewHeight / viewWidth > height / width) {
            newHeight = (int) (viewWidth * rate);
        } else {
            newWidth = (int) (viewHeight / rate);
        }
        line = (line * newWidth) / width;
        return Bitmap.createScaledBitmap(source, (int) newWidth, (int) newHeight, true);
    }

    /**
     * 좌표 리스트를 텍스트 파일로 생성
     * @param filename
     * @param element
     */
    public void saveTextFile(String filename, ObjectBuffer.Element element) {
        try {
            File dir = new File(getExternalCacheDir().toString());
            //디렉토리 폴더가 없으면 생성함
            if (!dir.exists()) {
                dir.mkdir();
            }
            //파일 output stream 생성
            FileOutputStream fos = new FileOutputStream(getExternalCacheDir() + "/" + filename, true);
            //파일쓰기
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));

            String mode = objectBuffer.getMode();
            float width = (float)element.getBitmap().getWidth();
            float height = (float)element.getBitmap().getHeight();

            writer.write(String.format("%s\n", mode));
            writer.write(String.format("%f %f\n", width / 1000, height / 1000));

            if (mode.equals("SYMMETRY")) {
                float axisF = imageView.getLine() / 1000;
                writer.write(String.format("%f\n", axisF));
                for (Coordinates c : element.getList()) {
                    writer.write(String.format("%f\n%f\n", c.getX(), c.getY()));
                    writer.flush();
                }
            } else {
                writer.write(String.format("%s\n", axis));
                for (Coordinates c : element.getList()) {
                    writer.write(String.format("%f %f\n", c.getX(), c.getY()));
                }
            }

            writer.close();
            fos.close();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "파일 쓰기 오류 발생", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 서버에 전송할 물체 조각 텍스쳐 생성 후 저장
     * @param filename
     * @param image
     * @return
     */
    public Bitmap saveTextureFile(String filename, Bitmap image) {
        try {
            File f = new File(getExternalCacheDir().toString(), filename);
            //디렉토리 폴더가 없으면 생성함

            FileOutputStream out = new FileOutputStream(f);
            image.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();
        } catch (Exception e) {

        }
        return null;
    }

    /**
     * 1. 원본 이미지 저장 후 전송
     *  * n번 반복 *
     * 2. n번째 좌표 리스트 저장 후 전송
     * 3. n번째 텍스처 이미지 저장 후 전송
     * @param name
     */
    private void upload(String name) {
        String path = getExternalCacheDir() + "/";
        NetworkStatus status = new NetworkStatus(this);
        if (!status.isConnected()) {
            Toast.makeText(this, R.string.network_not_connected, Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        EditText editText = new EditText(this);
        builder.setView(editText);
        builder.setMessage(R.string.name_setting);
        builder.setCancelable(false);
        
        builder.setPositiveButton(R.string.save, (dialogInterface, i) -> {
            String text = editText.getText().toString();
            
            // 원본 이미지 전송
            saveTextureFile(filename + ".jpg", objectBuffer.getOriginalImage());
            File file = new File(path + filename + ".jpg");
            transfer.upload(R.string.s3_bucket, FilenameUtils.getBaseName(text + ".jpg")
                    .isEmpty() ? file.getName() : text + ".jpg", file);

            int pos = 0;
            for (ObjectBuffer.Element element : objectBuffer.getBuffer()) {
                // 좌표 리스트 저장 후 전송
                file = new File(path + String.format("%s-%d.txt", filename, pos));
                saveTextFile(String.format("%s-%d.txt", filename, pos), element);
                transfer.upload(R.string.s3_bucket, FilenameUtils.getBaseName(text + ".txt")
                        .isEmpty() ? file.getName() : text + ".txt", file);
                
                // 물체 조각 텍스쳐 저장 후 전송
                file = new File(path + String.format("%s-%d.jpg", filename, pos));
                saveTextureFile(String.format("%s-%d.jpg", filename, pos),  element.getBitmap());
                transfer.upload(R.string.s3_bucket, FilenameUtils.getBaseName(text + ".txt")
                        .isEmpty() ? file.getName() : text + ".jpg", file);

                pos++;
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
        });
        builder.show();
    }

    /**
     * SYMMETRY 모드일 때 축 선택 후 전송
     */
    protected void selectAxis() {
        final String[] menu = {getString(R.string.axis_x), getString(R.string.axis_y)};
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.photo_type);
        alertDialogBuilder.setItems(menu, (dialogInterface, i) -> {
            Bitmap bitmap;
            switch (i) {
                case 0:
                    axis = "X";
                    resultList = imageView.getPairX();
                    break;
                case 1:
                    axis = "Y";
                    resultList = imageView.getPairY();
                    break;
                default:
                    break;
            }

            bitmap = imageView.getCroppedImage().copy(Bitmap.Config.ARGB_8888, true);
            if (resultList != null) {
                List newList = new ArrayList<Coordinates>(resultList);
                objectBuffer.push(bitmap, newList, axis);
                Toast.makeText(this, "추가 완료", Toast.LENGTH_SHORT).show();
            }
        });
        alertDialogBuilder.setCancelable(false);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    protected class BackgroundThread extends Thread {
        volatile boolean running = false;
        int cnt;

        void setRunning(boolean b) {
            running = b;
            cnt = 7;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    sleep(500);
                    if (cnt-- == 0) {
                        running = false;
                    }
                } catch (InterruptedException e) {

                }
            }
            handler.sendMessage(handler.obtainMessage());
        }
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            alertDialog.dismiss();

            boolean retry = true;
            while (retry) {
                try {
                    backgroundThread.join();
                    retry = false;
                } catch (InterruptedException e) {

                }
            }
        }
    };
}