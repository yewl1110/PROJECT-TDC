package com.little_wizard.tdc.ui.draw;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.little_wizard.tdc.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * SYMMETRY모드: CameraActivity에서 선택or촬영한 이미지 가져온 후 축 표시
 *
 * EditActivity - 대칭 축이 구해진 이미지를 DrawActivity로 넘김
 * EditImageView - 이미지에서 모델링 할 대칭인 물체의 대칭 축의 x 좌표를 구함
 */
public class EditActivity extends AppCompatActivity {
    EditImageView imageView;
    String TAG = getClass().getSimpleName();
    String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageView = new EditImageView(this);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(getApplicationContext().getContentResolver(), uri);
            Bitmap bitmap = ImageDecoder.decodeBitmap(source);
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            imageView.setBitmap(bitmap, getWindowManager().getDefaultDisplay());
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "오류", Toast.LENGTH_LONG);
        }

        setContentView(R.layout.activity_draw);

        LinearLayout layout = findViewById(R.id.layout_draw);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        );
        layout.addView(imageView, params);
        layout.invalidate();

        // 중앙 축 추가하는 코드
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout linear = (LinearLayout) inflater.inflate(R.layout.line, null);
        //LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        addContentView(linear, params);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_edit, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // 사진 축 설정 후 완료 버튼 눌렀을 때
            case R.id.edit_done: {
                Bitmap bitmap = imageView.getResult();
                if (bitmap != null) {
                    String bitName = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
                    saveBitmap(bitName, bitmap);

                    Intent intent = new Intent(this, DrawActivity.class);
                    intent.setAction(Intent.ACTION_SEND);
                    intent.putExtra("MODE", "SYMMETRY");
                    intent.putExtra("LINE", imageView.getLinePosX());
                    intent.setData(Uri.fromFile(new File(filePath)));
                    startActivity(intent);
                    finish();
                }
                Toast.makeText(getApplicationContext(), "이미지 없음", Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void saveBitmap(String bitName, Bitmap mBitmap) {//  ww  w.j  a va 2s.c  o  m

        File f = new File(getExternalCacheDir() + "/" + bitName + ".png");
        try {
            f.createNewFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
        }
        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fout);
        try {
            fout.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        filePath = f.getPath();
    }
}