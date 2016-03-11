package com.pluscubed.plustimer.ui.solvelist;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.base.RecyclerViewUpdate;
import com.pluscubed.plustimer.model.Solve;
import com.pluscubed.plustimer.utils.PrefUtils;
import com.pluscubed.plustimer.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Acts as a MVP view (sort of...)
 */
public class SolveListAdapter extends RecyclerView.Adapter<SolveListAdapter.ViewHolder>
        implements SolveListAdapterView {

    private static final String STATE_SOLVES = "state_solves";
    private static final String STATE_BESTWORST = "state_bestandworstsolves";
    private static final String STATE_STATS = "state_stats";
    private static final String STATE_INITIALIZED = "state_initialized";

    private static final int HEADER_VIEWTYPE = 2;
    private static final int HEADER_ID = -1;
    private final Solve[] mBestAndWorstSolves;
    private Context mContext;
    private String mPuzzleTypeId;
    private List<Solve> mSolves;
    private String mStats;

    private boolean mSignEnabled;
    private boolean mMillisecondsEnabled;
    private boolean mHeaderEnabled;

    private SolveListPresenter mPresenter;

    private boolean mInitialized;

    public SolveListAdapter(Context context, Bundle savedInstanceState) {
        mContext = context;

        if (savedInstanceState != null) {
            mBestAndWorstSolves = (Solve[]) savedInstanceState.getParcelableArray(STATE_BESTWORST);
            mSolves = savedInstanceState.getParcelableArrayList(STATE_SOLVES);
            mStats = savedInstanceState.getString(STATE_STATS);
            mInitialized = savedInstanceState.getBoolean(STATE_INITIALIZED);
        } else {
            mBestAndWorstSolves = new Solve[2];
            mSolves = new ArrayList<>();
            mStats = "";
            mInitialized = false;
        }

        updateSignAndMillisecondsMode();

        setHasStableIds(true);
    }

    @Override
    public boolean isInitialized() {
        return mInitialized;
    }

    public void setSolves(String puzzleTypeId, List<Solve> solves) {
        mPuzzleTypeId = puzzleTypeId;
        mSolves = solves;
    }

    public void setHeaderEnabled(boolean headerEnabled) {
        mHeaderEnabled = headerEnabled;
    }

    public void onPresenterPrepared(SolveListPresenter presenter) {
        mPresenter = presenter;
    }

    public void onPresenterDestroyed() {
        mPresenter = null;
    }

    public void onSaveInstanceState(Bundle outState) {
        //TODO: TransactionTooLargeException is possible, but this is linked to the more serious problem of how much data is stored in memory in general
        outState.putParcelableArrayList(STATE_SOLVES, (ArrayList<Solve>) mSolves);
        outState.putString(STATE_STATS, mStats);
        outState.putParcelableArray(STATE_BESTWORST, mBestAndWorstSolves);
        outState.putBoolean(STATE_INITIALIZED, mInitialized);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == HEADER_VIEWTYPE) {
            view = LayoutInflater.from(mContext).inflate(R.layout.list_item_solvelist_header, parent, false);
        } else {
            view = LayoutInflater.from(mContext).inflate(R.layout.list_item_solvelist, parent, false);
        }
        return new ViewHolder(view, viewType == HEADER_VIEWTYPE);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (!mHeaderEnabled || position > 0) {

            if (mHeaderEnabled)
                position = position - 1;

            Solve s = mSolves.get(position);
            String timeString = s.getTimeString(mMillisecondsEnabled);
            holder.textView.setText(timeString);

            for (Solve solve : mBestAndWorstSolves) {
                if (s == solve) {
                    holder.textView.setText("(" + timeString + ")");
                    break;
                }
            }

            String uiScramble = Utils.getUiScramble(s.getScramble(), mSignEnabled, mPuzzleTypeId);

            holder.desc.setText(uiScramble);

        } else {
            holder.header.setText(mStats);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return HEADER_VIEWTYPE;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return mSolves.size() + getHeaderOffset();
    }

    @Override
    public long getItemId(int position) {
        if (mHeaderEnabled)
            if (position == 0)
                return HEADER_ID;
            else
                return mSolves.get(position - 1).getId().hashCode();
        else
            return mSolves.get(position).getId().hashCode();
    }

    private int getHeaderOffset() {
        return mHeaderEnabled ? 1 : 0;
    }

    @Override
    public void notifyChange(RecyclerViewUpdate mode, Solve solve, String stats) {
        int oldSize = mSolves.size();

        switch (mode) {
            case DATA_RESET:
                notifyDataSetChanged();
                break;
            case INSERT:
                mSolves.add(0, solve);
                notifyItemInserted(getHeaderOffset());
                break;
            case REMOVE:
                mSolves.remove(solve);

                notifyDataSetChanged();
                break;
            case SINGLE_CHANGE:
                for (int i = 0; i < mSolves.size(); i++) {
                    Solve foundSolve = mSolves.get(i);
                    if (foundSolve.getId().equals(solve.getId())) {
                        mSolves.set(i, solve);
                        notifyItemChanged(i + getHeaderOffset());
                        break;
                    }
                }
                break;
            case REMOVE_ALL:
                mSolves.clear();

                notifyItemRangeRemoved(getHeaderOffset(), oldSize);
                break;
        }

        Solve oldBest = mBestAndWorstSolves[0];
        Solve oldWorst = mBestAndWorstSolves[1];
        Solve newBest = Utils.getBestSolveOfList(mSolves);
        Solve newWorst = Utils.getWorstSolveOfList(mSolves);
        mBestAndWorstSolves[0] = newBest;
        mBestAndWorstSolves[1] = newWorst;

        if (mode == RecyclerViewUpdate.INSERT || mode == RecyclerViewUpdate.SINGLE_CHANGE) {
            if (oldBest != null && !oldBest.equals(newBest)) {
                //indexOf old solve will only work for insert b/c it uses .equals of Solve,
                // but that's fine since in single change the old solve is updated already
                notifyItemChanged(mSolves.indexOf(oldBest) + getHeaderOffset());
                notifyItemChanged(mSolves.indexOf(newBest) + getHeaderOffset());
            }
            if (oldWorst != null && !oldWorst.equals(newWorst)) {
                notifyItemChanged(mSolves.indexOf(oldWorst) + getHeaderOffset());
                notifyItemChanged(mSolves.indexOf(newWorst) + getHeaderOffset());
            }
        }

        mStats = stats;
        notifyItemChanged(0);
    }

    @Override
    public void updateSignAndMillisecondsMode() {
        boolean signWasEnabled = mSignEnabled;
        boolean millisecondsWasEnabled = mMillisecondsEnabled;

        mSignEnabled = PrefUtils.isSignEnabled(mContext);
        mMillisecondsEnabled = PrefUtils.isDisplayMillisecondsEnabled(mContext);

        if (signWasEnabled != mSignEnabled || mMillisecondsEnabled != millisecondsWasEnabled) {
            notifyItemRangeChanged(getHeaderOffset(), mSolves.size());
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;
        public TextView desc;
        public TextView header;

        public ViewHolder(View v, boolean header) {
            super(v);
            if (header) {
                this.header = (TextView) v.findViewById(R.id.solvelist_header_stats_textview);
            } else {
                textView = (TextView) v.findViewById(R.id.list_item_solvelist_title_textview);
                desc = (TextView) v.findViewById(R.id.list_item_solvelist_desc_textview);

                v.setOnClickListener(view -> {
                    mPresenter.onSolveClicked(mSolves.get(getAdapterPosition() - getHeaderOffset()));
                });
            }
        }
    }


}
