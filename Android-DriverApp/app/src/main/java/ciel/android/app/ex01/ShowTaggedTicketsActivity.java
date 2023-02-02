package ciel.android.app.ex01;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import ciel.android.libs.bluetooth.FragmentShowTaggedTickets;

public class ShowTaggedTicketsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_tagged_tickets);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, FragmentShowTaggedTickets.newInstance("E0:5A:1B:77:2D:5E")) // HC06="98:DA:60:03:8A:45", CLPAT-WROOM-mini=E0:5A:1B:77:2D:5E
                    .commitNow();
        }
    }
}