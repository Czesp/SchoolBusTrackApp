package com.example.schoolbus.driver;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

@SuppressLint("CustomSplashScreen")
public class SplashScreen extends AppCompatActivity {
    ImageView Roadmap,BusImage;
    TextView text1,text2;
    Animation Top_anim,Bottom_anim,Left_anim;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        Roadmap=findViewById(R.id.roadmap);
        BusImage=findViewById(R.id.bus);

        text1=findViewById(R.id.text1);
        text2=findViewById(R.id.descrip);

        Top_anim= AnimationUtils.loadAnimation(this,R.anim.topanim);
        Bottom_anim= AnimationUtils.loadAnimation(this, R.anim.bottomanim);
        Left_anim= AnimationUtils.loadAnimation(this,R.anim.leftanim);

        Roadmap.setAnimation(Top_anim);
        BusImage.setAnimation(Left_anim);
        text1.setAnimation(Bottom_anim);
        text2.setAnimation(Bottom_anim);

        new android.os.Handler().postDelayed(() -> {
            startActivity(new Intent(SplashScreen.this, LoginActivity.class));
            finish();
        }, 4000);
    }
}
