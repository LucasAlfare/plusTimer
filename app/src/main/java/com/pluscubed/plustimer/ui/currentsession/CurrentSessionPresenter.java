package com.pluscubed.plustimer.ui.currentsession;

import android.widget.Toast;

import com.pluscubed.plustimer.MvpPresenter;
import com.pluscubed.plustimer.model.PuzzleType;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

public class CurrentSessionPresenter extends MvpPresenter<CurrentSessionView> {

    public void onCreate() {
        PuzzleType.initialize(getView().getContextCompat())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Object>() {
                    @Override
                    public void onCompleted() {
                        if (isViewAttached()) {
                            getView().getCurrentSessionTimerFragment().getPresenter().setInitialized();
                            getView().getSolveListFragment().getPresenter()
                                    .setInitialized(PuzzleType.getCurrentId(),
                                            PuzzleType.getCurrent().getCurrentSessionId());
                            getView().supportInvalidateOptionsMenu();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (isViewAttached()) {
                            Toast.makeText(getView().getContextCompat(), e.getMessage(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onNext(Object o) {

                    }
                });
    }
}
