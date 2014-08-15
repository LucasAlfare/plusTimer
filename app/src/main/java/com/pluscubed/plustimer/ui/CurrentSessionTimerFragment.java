package com.pluscubed.plustimer.ui;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.model.PuzzleType;
import com.pluscubed.plustimer.model.Session;
import com.pluscubed.plustimer.model.Solve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import it.sephiroth.android.library.widget.HListView;

/**
 * TimerFragment
 */

public class CurrentSessionTimerFragment extends Fragment implements CurrentSessionTimerRetainedFragment.Callback {
    public static final String TAG = "CURRENT_SESSION_TIMER_FRAGMENT";

    private static final String STATE_IMAGE_DISPLAYED = "scramble_image_displayed_boolean";
    private static final String STATE_START_TIME = "start_time_long";
    private static final String STATE_RUNNING = "running_boolean";
    private static final String STATE_INSPECTING = "inspecting_boolean";
    private static final String STATE_INSPECTION_START_TIME = "inspection_start_time_long";

    private CurrentSessionTimerRetainedFragment mRetainedFragment;

    private TextView mTimerText;
    private TextView mScrambleText;
    private HListView mHListView;
    private ImageView mScrambleImage;
    private TextView mStatsSolvesText;
    private TextView mStatsText;
    private TextView mInspectingText;

    private Handler mUiHandler;

    private boolean mHoldToStartOn;
    private boolean mHoldTimerStarted;
    private long mHoldTimerStartTimestamp;
    private final Runnable mHoldTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (500000000L <= System.nanoTime() - mHoldTimerStartTimestamp) {
                mTimerText.setTextColor(Color.GREEN);
            } else {
                mUiHandler.postDelayed(this, 10);
            }
        }
    };

    private boolean mInspectionOn;
    private boolean mInspecting;
    private long mInspectionStartTimestamp;
    private long mStartTimestamp;
    private final Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            mTimerText.setText(Solve.timeStringFromLong(System.nanoTime() - mStartTimestamp));
            mUiHandler.postDelayed(this, 10);
        }
    };


    private long mEndTimestamp;
    private long mFinalTime;
    private boolean mFromSavedInstanceState;
    private boolean mRunning;
    private boolean mScrambleImageDisplay;
    private boolean mLateStartPenalty;
    private final Runnable mInspectionRunnable = new Runnable() {
        @Override
        public void run() {
            mTimerText.setText(Solve.timeStringFromLong(15000000000L - (System.nanoTime() - mInspectionStartTimestamp)));
            if (15000000000L - (System.nanoTime() - mInspectionStartTimestamp) <= 0) {
                mLateStartPenalty = true;
                mTimerText.setText(getString(R.string.plus_two));
                if (17000000000L - (System.nanoTime() - mInspectionStartTimestamp) <= 0) {
                    mLateStartPenalty = false;
                    mInspecting = false;
                    mInspectingText.setVisibility(View.GONE);
                    mHoldTimerStarted = false;
                    //Stop keeping the screen on
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    //stop the runnables
                    mUiHandler.removeCallbacksAndMessages(null);

                    Solve s = new Solve(mRetainedFragment.getCurrentScrambleAndSvg(), 0);
                    s.setPenalty(Solve.Penalty.DNF);

                    //Add the solve to the current session with the current scramble/scramble image and DNF
                    PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().addSolve(s);

                    mTimerText.setText(s.getTimeString());

                    //Update stats and HListView
                    onSessionSolvesChanged();

                    if (mRetainedFragment.isScrambling()) {
                        mScrambleText.setText(R.string.scrambling);
                    }
                    mTimerText.setTextColor(Color.BLACK);
                    mRetainedFragment.updateViews();
                    return;
                }
            }
            mUiHandler.postDelayed(this, 10);
        }
    };

    //Generate string with specified current averages and mean of current session
    public static String buildStatsWithAveragesOf(Context context, Integer... currentAverages) {
        Arrays.sort(currentAverages, Collections.reverseOrder());
        String s = "";
        for (int i : currentAverages) {
            if (PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getNumberOfSolves() >= i) {
                s += context.getString(R.string.cao) + i + ": " + PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getStringCurrentAverageOf(i) + "\n";
            }
        }
        if (PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getNumberOfSolves() > 0) {
            s += context.getString(R.string.mean) + PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getStringMean();
        }
        return s;
    }


    /* Might be useful later
     public static double convertPxToDp(double px, DisplayMetrics metrics){
     double dp = px / (metrics.xdpi / (double)DisplayMetrics.DENSITY_DEFAULT);
     return dp;
     }

     public static double convertDpToPx(double dp, DisplayMetrics metrics){

     double px = dp * (metrics.xdpi /(double)DisplayMetrics.DENSITY_DEFAULT);
     return px;

     }
     */


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_IMAGE_DISPLAYED, mScrambleImageDisplay);
        outState.putLong(STATE_START_TIME, mStartTimestamp);
        outState.putBoolean(STATE_RUNNING, mRunning);
        outState.putBoolean(STATE_INSPECTING, mInspecting);
        outState.putLong(STATE_INSPECTION_START_TIME, mInspectionStartTimestamp);
    }

    //Set scramble text and scramble image to current ones
    @Override
    public void updateScrambleTextAndImageToCurrent() {
        SVG svg = null;
        try {
            svg = SVG.getFromString(mRetainedFragment.getCurrentScrambleAndSvg().svgLite.toString());
        } catch (SVGParseException e) {
            e.printStackTrace();
        }
        Drawable drawable = new PictureDrawable(svg.renderToPicture());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mScrambleImage.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mScrambleImage.setImageDrawable(drawable);

        mScrambleText.setText(mRetainedFragment.getCurrentScrambleAndSvg().scramble);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mRetainedFragment = getRetainedFragment();
        mRetainedFragment.setTimerFragmentCallback(this);

        //Set up UIHandler
        mUiHandler = new Handler(Looper.getMainLooper());

        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);

        if (savedInstanceState != null) {
            mScrambleImageDisplay = savedInstanceState.getBoolean(STATE_IMAGE_DISPLAYED);
            mStartTimestamp = savedInstanceState.getLong(STATE_START_TIME);
            mRunning = savedInstanceState.getBoolean(STATE_RUNNING);
            mInspecting = savedInstanceState.getBoolean(STATE_INSPECTING);
            mInspectionStartTimestamp = savedInstanceState.getLong(STATE_INSPECTION_START_TIME);
            mFromSavedInstanceState = true;
        } else {
            mFromSavedInstanceState = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //When destroyed, stop timer runnable
        mUiHandler.removeCallbacksAndMessages(null);
        mRetainedFragment.setTimerFragmentCallback(null);
    }


    public void onSessionSolvesChanged() {
        //Update stats
        mStatsSolvesText.setText(getString(R.string.solves) + PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getNumberOfSolves());
        mStatsText.setText(buildStatsWithAveragesOf(getActivity(), 5, 12, 100));
        //Update HListView
        ((SolveHListViewAdapter) mHListView.getAdapter()).updateSolvesList();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //Toggle image button
            case R.id.menu_activity_current_session_scramble_image_menuitem:
                toggleScrambleImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mInspectionOn = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(getString(R.string.pref_inspection_checkbox), true);
        mHoldToStartOn = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(getString(R.string.pref_holdtostart_checkbox), true);
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(getString(R.string.pref_keepscreenon_checkbox), true)) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void stopHoldTimer() {
        mUiHandler.removeCallbacks(mHoldTimerRunnable);
        mTimerText.setTextColor(Color.BLACK);

    }

    private CurrentSessionTimerRetainedFragment getRetainedFragment() {
        GetRetainedFragmentCallback callback;
        try {
            callback = (GetRetainedFragmentCallback) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement GetRetainedFragmentCallback");
        }
        return callback.getCurrentSessionTimerRetainedFragment();
    }

    //Called when the session is changed to another one (action bar spinner)
    public void onSessionChanged() {
        //Update quick stats and hlistview
        onSessionSolvesChanged();

        //Set timer text to ready, scramble text to scrambling
        mScrambleText.setText(R.string.scrambling);
        mTimerText.setText(R.string.ready);

        //Update options menu (disable)
        enableMenuItems(false);

        //Hide scramble image
        mScrambleImage.setVisibility(View.GONE);
        mScrambleImageDisplay = false;

        mRetainedFragment.resetScramblerThread();
        mRetainedFragment.generateNextScramble();
        mRetainedFragment.updateViews();

        resetTimer();
    }

    public void resetTimer() {
        mUiHandler.removeCallbacksAndMessages(null);
        mHoldToStartOn = false;
        mHoldTimerStarted = false;
        mRunning = false;
        mLateStartPenalty = false;
        mHoldTimerStartTimestamp = 0;
        mInspectionStartTimestamp = 0;
        mStartTimestamp = 0;
        mEndTimestamp = 0;
        mFinalTime = 0;
        mInspecting = false;

        mTimerText.setText(getString(R.string.ready));
        mTimerText.setTextColor(Color.BLACK);
        mInspectingText.setVisibility(View.GONE);
    }

    @Override
    public void enableMenuItems(boolean enable) {
        MenuItemsEnableCallback callback;
        try {
            callback = (MenuItemsEnableCallback) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString() + " must implement MenuItemsCallback");
        }
        callback.enableMenuItems(enable);
    }

    public void toggleScrambleImage() {
        if (mScrambleImageDisplay) {
            mScrambleImageDisplay = false;
            mScrambleImage.setVisibility(View.GONE);
        } else {
            if (!mRetainedFragment.isScrambling()) {
                mScrambleImageDisplay = true;
                mScrambleImage.setVisibility(View.VISIBLE);
                mScrambleImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mScrambleImageDisplay = false;
                        mScrambleImage.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_current_session_timer, container, false);

        mTimerText = (TextView) v.findViewById(R.id.fragment_current_session_timer_time_textview);
        mScrambleText = (TextView) v.findViewById(R.id.fragment_current_session_timer_scramble_textview);
        mScrambleImage = (ImageView) v.findViewById(R.id.fragment_current_session_timer_scramble_imageview);
        mHListView = (HListView) v.findViewById(R.id.fragment_current_session_timer_bottom_hlistview);


        mInspectingText = (TextView) v.findViewById(R.id.fragment_current_session_timer_inspecting_textview);

        mStatsText = (TextView) v.findViewById(R.id.fragment_current_session_timer_stats_textview);
        mStatsSolvesText = (TextView) v.findViewById(R.id.fragment_current_session_timer_stats_solves_number_textview);

        final SolveHListViewAdapter adapter = new SolveHListViewAdapter();
        mHListView.setAdapter(adapter);
        mHListView.setOnItemClickListener(new it.sephiroth.android.library.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id) {
                try {
                    CreateDialogCallback callback = (CreateDialogCallback) getActivity();
                    callback.createSolveDialog(null, 0, position);
                } catch (ClassCastException e) {
                    throw new ClassCastException(getActivity().toString()
                            + " must implement OnDialogDismissedListener");
                }
            }
        });


        //When the timer text is touched...
        mTimerText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        if (mRunning) {
                            //Record the ending time, set flag to false, and stop the timer runnable
                            mEndTimestamp = System.nanoTime();
                            mRunning = false;
                            mUiHandler.removeCallbacksAndMessages(null);

                            //Set the timer text to total time
                            mFinalTime = mEndTimestamp - mStartTimestamp;

                            Solve s = new Solve(mRetainedFragment.getCurrentScrambleAndSvg(), mFinalTime);
                            if (mInspectionOn && mLateStartPenalty) {
                                s.setPenalty(Solve.Penalty.PLUSTWO);
                                mLateStartPenalty = false;
                            }
                            //Add the solve to the current session with the current scramble/scramble image and time
                            PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().addSolve(s);

                            mTimerText.setText(s.getTimeString());

                            //Update stats and HListView
                            onSessionSolvesChanged();

                            if (mRetainedFragment.isScrambling()) {
                                mScrambleText.setText(R.string.scrambling);
                            }

                            mRetainedFragment.updateViews();
                            return false;
                        } else if (mInspectionOn && !mInspecting && !mRetainedFragment.isScrambling()) {
                            return true;
                        } else if (mInspecting || (mHoldToStartOn && !mRetainedFragment.isScrambling())) {
                            mHoldTimerStarted = true;
                            mHoldTimerStartTimestamp = System.nanoTime();
                            mTimerText.setTextColor(Color.RED);
                            mUiHandler.postDelayed(mHoldTimerRunnable, 450);
                            return true;
                        } else if (!mInspectionOn && !mHoldToStartOn && !mRetainedFragment.isScrambling()) {
                            return true;
                        }
                        return false;
                    }

                    case MotionEvent.ACTION_UP: {
                        if (mInspectionOn && !mInspecting && !mRunning) {
                            mInspectionStartTimestamp = System.nanoTime();
                            mInspecting = true;
                            mInspectingText.setVisibility(View.VISIBLE);
                            mUiHandler.post(mInspectionRunnable);
                            enableMenuItems(false);
                            //Set the scramble image to gone
                            mScrambleImage.setVisibility(View.GONE);
                            mScrambleImageDisplay = false;
                            //Post to scrambler thread: generate the next scramble
                            mRetainedFragment.generateNextScramble();
                        } else if (((mInspectionOn && mInspecting) || mHoldToStartOn) && !mRunning) {
                            if (mHoldTimerStarted && 500000000L <= System.nanoTime() - mHoldTimerStartTimestamp) {
                                mStartTimestamp = System.nanoTime();
                                mInspecting = false;
                                mInspectingText.setVisibility(View.GONE);
                                mRunning = true;
                                mUiHandler.removeCallbacksAndMessages(null);
                                mUiHandler.post(mTimerRunnable);
                                if (mHoldToStartOn) {
                                    enableMenuItems(false);
                                    //Set the scramble image to gone
                                    mScrambleImage.setVisibility(View.GONE);
                                    mScrambleImageDisplay = false;
                                    //Post to scrambler thread: generate the next scramble
                                    mRetainedFragment.generateNextScramble();
                                }
                            } else {
                                mHoldTimerStarted = false;
                                mUiHandler.removeCallbacks(mHoldTimerRunnable);
                            }
                            mTimerText.setTextColor(Color.BLACK);
                        } else if (!mInspectionOn && !mHoldToStartOn && !mRunning) {
                            enableMenuItems(false);
                            //Set the scramble image to gone
                            mScrambleImage.setVisibility(View.GONE);
                            mScrambleImageDisplay = false;
                            //Post to scrambler thread: generate the next scramble
                            mRetainedFragment.generateNextScramble();
                            mStartTimestamp = System.nanoTime();
                            mRunning = true;
                            mUiHandler.removeCallbacksAndMessages(null);
                            mUiHandler.post(mTimerRunnable);
                        }
                        return false;
                    }

                    default:
                        return false;
                }
            }
        });

        //When the application is initializing, disable action bar and generate a scramble.

        if (!mFromSavedInstanceState) {
            mRetainedFragment.resetScramblerThread();
            enableMenuItems(false);
            mScrambleText.setText(R.string.scrambling);
            mRetainedFragment.generateNextScramble();
            mRetainedFragment.updateViews();
        } else {
            if (mInspecting) {
                mUiHandler.post(mInspectionRunnable);
                mInspectingText.setVisibility(View.VISIBLE);
            }
            if (mRunning) {
                mUiHandler.post(mTimerRunnable);
            }
            if (mRunning || mInspecting) {
                enableMenuItems(false);
            } else if (!mRetainedFragment.isScrambling()) {
                enableMenuItems(true);
            }
            if (mInspecting || mRunning || !mRetainedFragment.isScrambling()) {
                // If timer is running, then update text/image to current. If timer is not running and not scrambling, then update scramble views to current.
                updateScrambleTextAndImageToCurrent();
            } else {
                mScrambleText.setText(R.string.scrambling);
            }
        }


        //If the current session has solves recorded, set the timer text to the latest solve's time.
        if (PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getNumberOfSolves() != 0) {
            mTimerText.setText(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getLastSolve().getTimeString());
        }

        //If the scramble image is currently displayed and it is not scrambling, then make sure it is set to visible and that the OnClickListener is set to toggling it; otherwise, set to gone.
        if (mScrambleImageDisplay) {
            if (!mRetainedFragment.isScrambling()) {
                mScrambleImage.setVisibility(View.VISIBLE);
                mScrambleImageDisplay = true;
                mScrambleImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleScrambleImage();
                    }
                });
            }
        } else {
            mScrambleImage.setVisibility(View.GONE);
            mScrambleImageDisplay = false;
        }

        onSessionSolvesChanged();

        return v;
    }

    @Override
    public Handler getUiHandler() {
        return mUiHandler;
    }


    public interface GetRetainedFragmentCallback {
        CurrentSessionTimerRetainedFragment getCurrentSessionTimerRetainedFragment();
    }

    public interface MenuItemsEnableCallback {
        void enableMenuItems(boolean enable);
    }

    public class SolveHListViewAdapter extends ArrayAdapter<Solve> {

        private ArrayList<Solve> mBestAndWorstSolves;

        public SolveHListViewAdapter() {
            super(getActivity(), 0, PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves());
            mBestAndWorstSolves = new ArrayList<Solve>();
            mBestAndWorstSolves.add(Session.getBestSolve(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves()));
            mBestAndWorstSolves.add(Session.getWorstSolve(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves()));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R.layout.hlist_item_solve, parent, false);
            }
            Solve s = getItem(position);
            TextView time = (TextView) convertView.findViewById(R.id.hlist_item_solve_textview);

            time.setText("");

            for (Solve a : mBestAndWorstSolves) {
                if (a == s) {
                    time.setText("(" + s.getTimeString() + ")");
                }
            }

            if (time.getText() == "") {
                time.setText(s.getTimeString());
            }

            return convertView;
        }

        public void updateSolvesList() {
            clear();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                addAll(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves());
            else {
                for (Solve i : PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves()) {
                    add(i);
                }
            }

            mBestAndWorstSolves = new ArrayList<Solve>();
            mBestAndWorstSolves.add(Session.getBestSolve(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves()));
            mBestAndWorstSolves.add(Session.getWorstSolve(PuzzleType.get(PuzzleType.CURRENT).getCurrentSession().getSolves()));
            notifyDataSetChanged();
        }


    }


}