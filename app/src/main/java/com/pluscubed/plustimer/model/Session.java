package com.pluscubed.plustimer.model;

import android.content.Context;

import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session data
 */
public class Session {
    public static final int GET_AVERAGE_INVALID = -2;

    private ArrayList<Solve> mSolves;

    public Session() {
        mSolves = new ArrayList<Solve>();
    }

    public int getPosition(Solve i) {
        return mSolves.indexOf(i);
    }

    public List<Solve> getSolves() {
        return new ArrayList<Solve>(Collections.unmodifiableList(mSolves));
    }

    public void addSolve(Solve s) {
        mSolves.add(s);
    }

    public int getNumberOfSolves() {
        return mSolves.size();
    }

    public Solve getLastSolve() {
        return mSolves.get(mSolves.size() - 1);
    }

    public Solve getSolveByPosition(int position) {
        return mSolves.get(position);
    }

    public String getStringCurrentAverageOf(int number) {
        if (number >= 5) {
            long sum = 0;
            ArrayList<Solve> solves = new ArrayList<Solve>();

            int dnfcount = 0;

            for (int i = 1; i < number + 1; i++) {
                Solve x = mSolves.get(mSolves.size() - i);
                solves.add(x);
                if (x.getPenalty() == Solve.Penalty.DNF)
                    dnfcount++;
            }

            if (dnfcount < 2) {
                solves.remove(Util.getBestSolveOfList(new ArrayList<Solve>(solves)));
                solves.remove(Util.getWorstSolveOfList(new ArrayList<Solve>(solves)));
                for (Solve i : solves) {
                    sum += i.getTimeTwo();
                }
                return Util.timeStringFromNanoseconds(sum / (number - 2L));
            } else {
                return "DNF";
            }
        }
        return "";
    }

    public long getAverageOf(List<Solve> list, int number) {
        if (number > 2 && list.size() == number) {
            long sum = 0;
            ArrayList<Solve> solves = new ArrayList<Solve>(list);

            int dnfcount = 0;

            for (Solve s : list) {
                if (s.getPenalty() == Solve.Penalty.DNF) {
                    dnfcount++;
                }
            }

            if (dnfcount < 2) {
                solves.remove(Util.getBestSolveOfList(new ArrayList<Solve>(solves)));
                solves.remove(Util.getWorstSolveOfList(new ArrayList<Solve>(solves)));
                for (Solve i : solves) {
                    sum += i.getTimeTwo();
                }
                return sum / (number - 2L);
            } else {
                return Long.MAX_VALUE;
            }
        }
        return GET_AVERAGE_INVALID;
    }

    public String getStringBestAverageOf(int number) {
        long bestAverage = getBestAverageOf(number);
        if (bestAverage == GET_AVERAGE_INVALID) {
            return null;
        }
        if (bestAverage == Long.MAX_VALUE) {
            return "DNF";
        }
        return Util.timeStringFromNanoseconds(bestAverage);
    }

    public long getBestAverageOf(int number) {
        long bestAverage = -1;
        for (int i = 0; mSolves.size() - (number + i) >= 0; i++) {
            long average = getAverageOf(mSolves.subList(mSolves.size() - (number + i), mSolves.size() - i), number);
            if (average != GET_AVERAGE_INVALID) {
                if (average < bestAverage || bestAverage == -1) {
                    bestAverage = average;
                }
            }
        }
        if (bestAverage != -1 && bestAverage != Long.MAX_VALUE) {
            return bestAverage;
        } else if (bestAverage == Long.MAX_VALUE) {
            return bestAverage;
        }
        return GET_AVERAGE_INVALID;
    }

    public String getStringMean() {
        long sum = 0;
        boolean dnf = false;
        for (Solve i : mSolves) {
            if (!(i.getPenalty() == Solve.Penalty.DNF)) {
                sum += i.getTimeTwo();
            } else
                dnf = true;
        }
        if (!dnf)
            return Util.timeStringFromNanoseconds(sum / mSolves.size());
        else
            return "DNF";
    }

    public String getTimestampString(Context context) {
        return Util.timeDateStringFromTimestamp(context, getLastSolve().getTimestamp());
    }

    public long getTimestamp() {
        return getLastSolve().getTimestamp();
    }

    public void deleteSolve(int position) {
        mSolves.remove(position);
    }

    public void deleteSolve(Solve i) {
        mSolves.remove(i);
    }

    public String toString(Context context, String puzzleTypeDisplayName, boolean current, boolean displaySolves) {
        StringBuilder s = new StringBuilder();
        if (displaySolves) {
            s.append(puzzleTypeDisplayName).append("\n\n");
        }
        s.append(context.getString(R.string.number_solves)).append(getNumberOfSolves());
        if (getNumberOfSolves() > 0) {
            s.append("\n").append(context.getString(R.string.mean)).append(getStringMean());
            if (getNumberOfSolves() > 1) {
                s.append("\n").append(context.getString(R.string.best)).append(Util.getBestSolveOfList(mSolves).getDescriptiveTimeString());
                s.append("\n").append(context.getString(R.string.worst)).append(Util.getWorstSolveOfList(mSolves).getDescriptiveTimeString());

                if (getNumberOfSolves() > 2) {
                    s.append("\n").append(context.getString(R.string.average)).append(Util.timeStringFromNanoseconds(getAverageOf(mSolves, mSolves.size())));

                    int[] averages = {1000, 100, 50, 12, 5};
                    for (int i : averages) {
                        if (getNumberOfSolves() >= i) {
                            if (current) {
                                s.append("\n").append(context.getString(R.string.cao)).append(i).append(": ").append(getStringCurrentAverageOf(i));
                            } else {
                                s.append("\n").append(context.getString(R.string.lao)).append(i).append(": ").append(getStringCurrentAverageOf(i));
                            }
                            s.append("\n").append(context.getString(R.string.bao)).append(i).append(": ").append(getStringBestAverageOf(i));
                        }
                    }
                }
            }
            if (displaySolves) {
                s.append("\n\n");
                int c = 1;
                for (Solve i : mSolves) {
                    Solve best = Util.getBestSolveOfList(mSolves);
                    Solve worst = Util.getWorstSolveOfList(mSolves);
                    s.append(c).append(". ");
                    if (i == best || i == worst) {
                        s.append("(").append(i.getDescriptiveTimeString()).append(")");
                    } else {
                        s.append(i.getDescriptiveTimeString());
                    }
                    s.append("\n")
                            .append("     ").append(Util.timeDateStringFromTimestamp(context, i.getTimestamp())).append("\n")
                            .append("     ").append(i.getScrambleAndSvg().scramble).append("\n\n");
                    c++;
                }
            }
        }
        return s.toString();
    }

}
