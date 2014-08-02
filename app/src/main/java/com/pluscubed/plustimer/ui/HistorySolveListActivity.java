package com.pluscubed.plustimer.ui;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.model.PuzzleType;
import com.pluscubed.plustimer.model.Solve;

/**
 * History session activity
 */
public class HistorySolveListActivity extends ActionBarActivity implements CurrentSBaseFragment.OnSolveItemClickListener, SolveDialog.SolveDialogListener {
    public static final String EXTRA_HISTORY_SESSION_POSITION = "com.pluscubed.plustimer.history_session_position";
    public static final String EXTRA_HISTORY_PUZZLETYPE_DISPLAYNAME = "com.pluscubed.plustimer.history_puzzletype_displayname";
    public static final String HISTORY_DIALOG_FRAGMENT_TAG = "HISTORY_MODIFY_DIALOG";

    @Override
    public void showCurrentSolveDialog(String displayName, int sessionIndex, int solveIndex) {
        DialogFragment dialog = (DialogFragment) getSupportFragmentManager().findFragmentByTag(HISTORY_DIALOG_FRAGMENT_TAG);
        if (dialog == null) {
            SolveDialog d = SolveDialog.newInstance(PuzzleType.get(displayName).toString(), sessionIndex, solveIndex);
            d.show(getSupportFragmentManager(), HISTORY_DIALOG_FRAGMENT_TAG);
        }
    }

    @Override
    public void onDialogDismissed(String displayName, int sessionIndex, int solveIndex, int penalty) {
        Solve solve = PuzzleType.get(displayName).getSession(sessionIndex, this).getSolveByPosition(solveIndex);
        switch (penalty) {
            case SolveDialog.DIALOG_PENALTY_NONE:
                solve.setPenalty(Solve.Penalty.NONE);
                break;
            case SolveDialog.DIALOG_PENALTY_PLUSTWO:
                solve.setPenalty(Solve.Penalty.PLUSTWO);
                break;
            case SolveDialog.DIALOG_PENALTY_DNF:
                solve.setPenalty(Solve.Penalty.DNF);
                break;
            case SolveDialog.DIALOG_RESULT_DELETE:
                PuzzleType.get(displayName).getSession(sessionIndex, this).deleteSolve(solveIndex);
                break;
        }

        if (getSupportFragmentManager().findFragmentById(R.id.activity_history_solve_list_framelayout) != null) {
            ((SolveListFragment) getSupportFragmentManager().findFragmentById(R.id.activity_history_solve_list_framelayout)).onSessionSolvesChanged();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_session);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.activity_history_solve_list_framelayout);
        if (fragment == null) {
            int position = getIntent().getIntExtra(EXTRA_HISTORY_SESSION_POSITION, 0);
            String puzzleType = getIntent().getStringExtra(EXTRA_HISTORY_PUZZLETYPE_DISPLAYNAME);
            fragment = SolveListFragment.newInstance(false, puzzleType, position);
            fm.beginTransaction().add(R.id.activity_history_solve_list_framelayout, fragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}