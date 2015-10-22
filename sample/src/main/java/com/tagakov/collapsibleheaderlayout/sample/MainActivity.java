package com.tagakov.collapsibleheaderlayout.sample;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.tagakov.collapsibleheaderlayout.CollapsibleHeaderLayout;

import java.util.HashMap;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private CollapsibleHeaderLayout chl;
    private static HashMap<Integer, Integer> cache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        chl = (CollapsibleHeaderLayout) findViewById(R.id.chl);
        chl.setCollapseListener(new CollapsibleHeaderLayout.CollapseListener() {
            @Override
            public void onCollapse(int currentHeight, float collapseFraction) {
                new Object();
                new Object();
                new Object();
            }

            @Override
            public void onOverDrag(int currentHeight, float overDragFraction) {

            }
        });
        initRecycler();
    }

    private void initRecycler() {
        final RecyclerView rv = (RecyclerView) findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.bottom = 32;
                outRect.top = 32;
                outRect.left = 32;
                outRect.right = 32;
            }
        });
        final Random rnd = new Random();
        rv.setAdapter(new RecyclerView.Adapter() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = new View(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200));
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                Integer color = cache.get(position);
                if (color == null) {
                    color = Color.rgb(
                            rnd.nextInt(255),
                            rnd.nextInt(255),
                            rnd.nextInt(255)
                    );
                    cache.put(position, color);
                }
                holder.itemView.setBackgroundColor(color);
            }

            @Override
            public int getItemCount() {
                return 35;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

}
