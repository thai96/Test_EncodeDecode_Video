package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<PickVisualMediaRequest> launcher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if(uri == null) return;
                Thread thread = new Thread(() -> {
                    VideoSaver videoSaver = new VideoSaver();
                    videoSaver.init(uri, this);
                    videoSaver.saveVideoToFile(this);
                    Toast.makeText(this, "Save end", Toast.LENGTH_LONG).show();
                });
                thread.start();
                Toast.makeText(this, "INput done", Toast.LENGTH_LONG).show();

            }
    );

    private ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {

            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button heel = findViewById(R.id.hello);
        String[] permission = {
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        List<String> permissions = new ArrayList<>();
        for(String p : permission){
            if(ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED){
                permissions.add(p);
            }
        }
        String[] arr = new String[permissions.size()];
        if(arr.length > 0){
            permissions.toArray(arr);
            requestPermissionLauncher.launch(arr);
        }
        heel.setOnClickListener(v -> {
            launcher.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE).build());
        });
    }


}