package com.vone.vmq;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.vone.qrcode.R;
import com.vone.vmq.util.Constant;
import com.google.zxing.activity.CaptureActivity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static java.util.Base64.*;

public class MainActivity extends AppCompatActivity {


    private TextView txthost;
    private TextView txtkey;
    private TextView txtwechat;
    private TextView txtalipay;

    private boolean isOk = false;
    private static String TAG = "MainActivity";

    private static String host;
    private static String key;
    private static String wechat;
    private static String alipay;
    private static String dataUrl;
    private static String heartUrl;


    int id = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        txthost = (TextView) findViewById(R.id.txt_host);
//        txtkey = (TextView) findViewById(R.id.txt_key);
        txtwechat = (TextView) findViewById(R.id.txt_wechat);
        txtalipay = (TextView) findViewById(R.id.txt_alipay);


        //检测通知使用权是否启用
        if (!isNotificationListenersEnabled()) {
            //跳转到通知使用权页面
            gotoNotificationAccessSetting();
        }
        //重启监听服务
        toggleNotificationListenerService(this);


        //读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        wechat = read.getString("wechat", "");
        alipay = read.getString("alipay", "");

        if (host != null && key != null && host != "" && key != "") {
            txthost.setText(" 通知地址：" + host);
//            txtkey.setText(" 通讯密钥：" + key);
            txtwechat.setText(" 微信号：" + wechat);
            txtalipay.setText(" 支付宝：" + alipay);
            isOk = true;
        }


        Toast.makeText(MainActivity.this, "v免签系统 v1.0.1", Toast.LENGTH_SHORT).show();


    }


    //扫码配置
    public void startQrCode(View v) {
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, Constant.REQ_PERM_CAMERA);
            return;
        }
        // 申请文件读写权限（部分朋友遇到相册选图需要读写权限的情况，这里一并写一下）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constant.REQ_PERM_EXTERNAL_STORAGE);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, Constant.REQ_QR_CODE);
    }

    //手动配置
    public void doInput(View v) {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入配置数据").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();
                // split[0[ http://xxxxxx split[1] keysssssssss
                String[] tmp = scanResult.split("/monitor/report/business/");
                if (tmp.length != 2) {
                    Toast.makeText(MainActivity.this, "数据错误，请您输入网站上显示的配置数据!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String decodeKey = NeNotificationService2.decode64(tmp[1]);

                host = scanResult;
                key = decodeKey;
                dataUrl = tmp[0] + Constant.URL_DATA + tmp[1];
                heartUrl = tmp[0] + Constant.URL_HEART + tmp[1];

                String url = NeNotificationService2.heartURL(heartUrl, decodeKey);



                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url(url).method("GET", null).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Log.d(TAG, "onResponse: " + response.body().string());
                        isOk = true;

                    }
                });
                if (tmp[0].indexOf("localhost") >= 0) {
                    Toast.makeText(MainActivity.this, "配置信息错误，本机调试请访问 本机局域网IP:8080(如192.168.1.101:8080) 获取配置信息进行配置!", Toast.LENGTH_LONG).show();

                    return;
                }
                //将扫描出的信息显示出来
                txthost.setText(" 通知地址：" + tmp[0]);
                txtkey.setText(" 通讯密钥：" + tmp[1]);

                SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
                editor.putString("host", host);
                editor.putString("key", key);
                editor.putString("heart", heartUrl);
                editor.putString("data", dataUrl);
                editor.commit();

            }
        });
        builder.show();

    }

    //检测心跳
    public void doStart(View view) {
        if (isOk == false) {
            Toast.makeText(MainActivity.this, "请您先配置!", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        heartUrl = read.getString("heart", "");

        String url = NeNotificationService2.heartURL(heartUrl, key);

        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(url).method("GET", null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳返回：成功 " + response.body().string(), Toast.LENGTH_LONG).show();
                Looper.loop();
            }
        });
    }

    //检测监听
    public void checkPush(View v) {

        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("1",
                    "Channel1", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);

            Notification.Builder builder = new Notification.Builder(this, "1");

            mNotification = builder
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        } else {
            mNotification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }

        //Toast.makeText(MainActivity.this, "已推送信息，如果权限，那么将会有下一条提示！", Toast.LENGTH_SHORT).show();


        mNotificationManager.notify(id++, mNotification);
    }

    // 配置微信
    public void doInputWechat(View v) {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入微信号").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();
//                Toast.makeText(MainActivity.this, scanResult, Toast.LENGTH_SHORT).show();

                wechat = scanResult;

                txtwechat.setText(" 微信号：" + wechat);

                SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
                editor.putString("wechat", wechat);
                editor.commit();
            }
        });
        builder.show();
    }

    // 配置支付宝
    public void doInputAlipay(View v) {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入支付宝").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();
//                Toast.makeText(MainActivity.this, scanResult, Toast.LENGTH_SHORT).show();

                alipay = scanResult;

                txtalipay.setText(" 支付宝：" + alipay);

                SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
                editor.putString("alipay", alipay);
                editor.commit();
            }
        });
        builder.show();
    }

    //各种权限的判断
    private void toggleNotificationListenerService(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Toast.makeText(MainActivity.this, "监听服务启动中...", Toast.LENGTH_SHORT).show();
    }

    public boolean isNotificationListenersEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean gotoNotificationAccessSetting() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;

        } catch (ActivityNotFoundException e) {//普通情况下找不到的时候需要再特殊处理找一次
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                startActivity(intent);
                return true;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            Toast.makeText(this, "对不起，您的手机暂不支持", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //扫描结果回调
        if (requestCode == Constant.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(Constant.INTENT_EXTRA_KEY_QR_SCAN);

            String[] tmp = scanResult.split("/monitor/report/business/");
            if (tmp.length != 2) {
                Toast.makeText(MainActivity.this, "二维码错误，请您扫描网站上显示的二维码!", Toast.LENGTH_SHORT).show();
                return;
            }

            String decodeKey = NeNotificationService2.decode64(tmp[1]);

            host = scanResult;
            key = decodeKey;
            dataUrl = tmp[0] + Constant.URL_DATA + tmp[1];
            heartUrl = tmp[0] + Constant.URL_HEART + tmp[1];

            String url = NeNotificationService2.heartURL(heartUrl, decodeKey);
//            Log.d(TAG, "-------------  URL地址: " + url);
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder().url(url).method("GET", null).build();
            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "onResponse: " + response.body().string());
                    isOk = true;

                }
            });

            //将扫描出的信息显示出来
            txthost.setText(" 通知地址：" + scanResult);


            SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
            editor.putString("host", host);
            editor.putString("key", key);
            editor.putString("heart", heartUrl);
            editor.putString("data", dataUrl);
            editor.commit();

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_CAMERA:
                // 摄像头权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case Constant.REQ_PERM_EXTERNAL_STORAGE:
                // 文件读写权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的文件读写权限", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }


}
