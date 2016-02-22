package com.pluscubed.plustimer.model;

import android.content.Context;
import android.support.annotation.NonNull;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.View;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.pluscubed.plustimer.App;
import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.utils.PrefUtils;
import com.pluscubed.plustimer.utils.Utils;

import net.gnehzr.tnoodle.scrambles.Puzzle;
import net.gnehzr.tnoodle.scrambles.PuzzlePlugins;
import net.gnehzr.tnoodle.utils.BadLazyClassDescriptionException;
import net.gnehzr.tnoodle.utils.LazyInstantiatorException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

/**
 * Puzzle Type object
 */
@JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.NONE,
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
public class PuzzleType extends CbObject {
    public static final String TYPE_PUZZLETYPE = "puzzletype";
    public static final String VIEW_PUZZLETYPES = "puzzletypes";

    private static String sCurrentTypeId;
    private static List<PuzzleType> sPuzzleTypes;

    private Puzzle mPuzzle;

    @JsonProperty("scrambler")
    private String mScrambler;
    @JsonProperty("currentSessionId")
    private String mCurrentSessionId;
    @JsonProperty("enabled")
    private boolean mEnabled;
    @JsonProperty("inspection")
    private boolean mInspectionOn;
    @JsonProperty("name")
    private String mName;
    @JsonProperty("bld")
    private boolean mIsBld;
    @JsonProperty("sessions")
    private List<String> mSessions;

    //Pre-SQL legacy code
    @Deprecated
    private String mCurrentSessionFileName;
    @Deprecated
    private String mHistoryFileName;
    @Deprecated
    private HistorySessions mHistorySessionsLegacy;
    @Deprecated
    private String mLegacyName;

    public PuzzleType(String id, String scrambler, String name, String currentSessionId,
                      boolean inspectionOn, boolean isBld) {
        mScrambler = scrambler;
        mName = name;
        mId = id;
        mEnabled = true;
        mCurrentSessionId = currentSessionId;
        mInspectionOn = inspectionOn;
        mIsBld = isBld;
        mSessions = new ArrayList<>();
    }

    public static List<PuzzleType> getPuzzleTypes() {
        return sPuzzleTypes;
    }

    public static PuzzleType get(String id) {
        for (PuzzleType type : sPuzzleTypes) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return sPuzzleTypes.get(0);
    }

    public static PuzzleType getCurrent() {
        return get(sCurrentTypeId);
    }

    public static void setCurrent(String currentId) {
        //TODO
        sCurrentTypeId = currentId;
    }

    public static String getCurrentId() {
        return sCurrentTypeId;
    }

    public static List<PuzzleType> getEnabledPuzzleTypes() {
        List<PuzzleType> array = new ArrayList<>();
        for (PuzzleType i : sPuzzleTypes) {
            if (i.isEnabled()) {
                array.add(i);
            }
        }
        return array;
    }

    public synchronized static Completable initialize(Context context) {
        try {
            if (sPuzzleTypes == null) {
                sPuzzleTypes = new ArrayList<>();


                int savedVersionCode = PrefUtils.getVersionCode(context);

                Database database = App.getDatabase(context);

                Completable completable;
                if (savedVersionCode < 24) {
                    initializePuzzleTypesFirstRun(context, database);
                    completable = Completable.complete();
                } else {
                    completable = initializePuzzleTypes(database);
                }

                for (PuzzleType puzzleType : sPuzzleTypes) {
                    //TODO: upgrade database
                    //puzzleType.upgradeDatabase(context);
                }

                PrefUtils.saveVersionCode(context);

                return completable;
            } else {
                return Completable.complete();
            }
        } catch (CouchbaseLiteException | IOException e) {
            return Completable.error(e);
        }
    }

    @NonNull
    private static Completable initializePuzzleTypes(Database database) {
        return Completable.create(subscriber -> {
            Query puzzleTypesQuery = database.getView(VIEW_PUZZLETYPES).createQuery();
            puzzleTypesQuery.runAsync((rows, error) -> {
                for (QueryRow row : rows) {
                    PuzzleType type = PuzzleType.fromDoc(row.getDocument(), PuzzleType.class);

                    if (sPuzzleTypes != null) {
                        sPuzzleTypes.add(type);
                    }

                    //TODO: Proper current type ID saving
                    if (type.getName().equals("3x3")) {
                        sCurrentTypeId = type.getId();
                    }
                }

                subscriber.onCompleted();
            });
        }).subscribeOn(Schedulers.io());
    }

    private static void initializePuzzleTypesFirstRun(Context context, Database database) throws CouchbaseLiteException, IOException {


        //Generate default puzzle types from this...
        String[] scramblers = context.getResources().getStringArray(R.array.scramblers);
        //and this, with the appropriate UI names...
        String[] defaultCustomPuzzleTypes = context.getResources()
                .getStringArray(R.array.default_custom_puzzletypes);
        //from this
        String[] puzzles = context.getResources().getStringArray(R.array.scrambler_names);
        //and the legacy names from this
        String[] legacyNames = context.getResources().getStringArray(R.array.legacy_names);


        for (int i = 0; i < scramblers.length + defaultCustomPuzzleTypes.length; i++) {
            String scrambler;
            String defaultCustomType = null;
            String uiName;
            boolean bld = false;

            if (scramblers.length > i) {
                scrambler = scramblers[i];
            } else {
                defaultCustomType = defaultCustomPuzzleTypes[i - scramblers.length];
                scrambler = defaultCustomType.substring(0, defaultCustomType.indexOf(","));
            }


            if (puzzles.length > i) {
                uiName = context.getResources().getStringArray(R.array.scrambler_names)[i];
            } else {
                int order = Integer.parseInt(scrambler.substring(0, 1));
                String addon = null;
                if (scrambler.contains("ni")) {
                    addon = context.getString(R.string.bld);
                    bld = true;
                }
                if (defaultCustomType != null) {
                    if (defaultCustomType.contains("feet")) {
                        addon = context.getString(R.string.feet);
                    } else if (defaultCustomType.contains("oh")) {
                        addon = context.getString(R.string.oh);
                    }
                }
                if (addon != null) {
                    uiName = order + "x" + order + "-" + addon;
                } else {
                    uiName = order + "x" + order;
                }
            }

            Document newDoc = database.createDocument();
            PuzzleType newPuzzleType = new PuzzleType(newDoc.getId(), scrambler, uiName, null, true, bld/*, legacyNames[i]*/);

            if (uiName.equals("3x3")) {
                //Default current puzzle type
                //currentPuzzleType.setValue(type.getId());
                //TODO: Save current type ID
                sCurrentTypeId = newPuzzleType.getId();
                newPuzzleType.newSession(context, false);
            }

            newDoc.putProperties(newPuzzleType.toMap());
            sPuzzleTypes.add(newPuzzleType);
        }

        View puzzletypesView = database.getView(VIEW_PUZZLETYPES);
        puzzletypesView.setMap((document, emitter) -> {
            if (document.get("type").equals(TYPE_PUZZLETYPE)) {
                emitter.emit(document.get("_id"), null);
            }
        }, "1");
    }

    private Session newSession(Context context, boolean update) throws IOException, CouchbaseLiteException {
        Session session = new Session(context);

        mCurrentSessionId = session.getId();
        mSessions.add(session.getId());

        if (update) {
            updateCb(context);
        }

        return session;
    }

    public String getCurrentSessionId() {
        return mCurrentSessionId;
    }

    public boolean isBld() {
        return mIsBld;
    }

    public boolean isScramblerOfficial() {
        return !mScrambler.contains("fast");
    }

    public String getScrambler() {
        return mScrambler;
    }

    public String getName() {
        return mName;
    }

    boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(Context context, boolean enabled) throws CouchbaseLiteException, IOException {
        //TODO
        this.mEnabled = enabled;
        if (mId.equals(sCurrentTypeId) && !this.mEnabled) {
            for (PuzzleType i : sPuzzleTypes) {
                if (i.mEnabled) {
                    sCurrentTypeId = i.getId();
                    break;
                }
            }
        }

        updateCb(context);
    }

    public String getId() {
        return mId;
    }

    @Deprecated
    public String getHistoryFileName() {
        return mHistoryFileName;
    }

    @Deprecated
    public String getCurrentSessionFileName() {
        return mCurrentSessionFileName;
    }

    //TODO
    /*@Deprecated
    public HistorySessions getHistorySessions() {
        return mHistorySessionsLegacy;
    }*/

    public void deleteSession(Context context, String sessionId) throws CouchbaseLiteException, IOException {
        getSession(context, sessionId)
                .getDocument(context)
                .delete();

        mSessions.remove(sessionId);

        updateCb(context);
    }

    public List<Session> getSortedHistorySessions() {
        //TODO
        /*if (sessions.size() > 0) {
            sessions.remove(getSession(mCurrentSessionId));
            Collections.sort(sessions, new Comparator<Session>() {
                @Override
                public int compare(Session lhs, Session rhs) {
                    if (lhs.getTimestamp() > rhs.getTimestamp()) {
                        return 1;
                    } else if (lhs.getTimestamp() < rhs.getTimestamp()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            });
        }
        return sessions;*/
        return null;
    }

    private void upgradeDatabase(Context context) {
        mHistoryFileName = mLegacyName + ".json";
        mCurrentSessionFileName = mLegacyName + "-current.json";
        mHistorySessionsLegacy = new HistorySessions(mHistoryFileName);

        //TODO
        //AFTER UPDATING APP////////////
        int savedVersionCode = PrefUtils.getVersionCode(context);

        if (savedVersionCode <= 10) {
            //Version <=10: Set up history sessions with old
            // name first
            if (!mScrambler.equals("333") || mLegacyName.equals("THREE")) {
                mHistorySessionsLegacy.setFilename(mScrambler + ".json");
                mHistorySessionsLegacy.init(context);
                mHistorySessionsLegacy.setFilename(mHistoryFileName);
                if (mHistorySessionsLegacy.getList().size() > 0) {
                    mHistorySessionsLegacy.save(context);
                }
                File oldFile = new File(context.getFilesDir(),
                        mScrambler + ".json");
                oldFile.delete();
            }
        }

        if (savedVersionCode <= 13) {
            //Version <=13: ScrambleAndSvg json structure changes
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Session.class,
                            (JsonDeserializer<Session>) (json, typeOfT, sessionJsonDeserializer) -> {

                                Gson gson1 = new GsonBuilder()
                                        .registerTypeAdapter(ScrambleAndSvg.class,
                                                (JsonDeserializer<ScrambleAndSvg>) (json1, typeOfT1, context1) ->
                                                        new ScrambleAndSvg(json1.getAsJsonPrimitive().getAsString(), null))
                                        .create();

                                Session s = gson1.fromJson(json, typeOfT);
                                /*for (final Solve solve : s.getSolves()) {
                                    //TODO: Legacy
                                    //solve.attachSession(s);
                                }*/
                                return s;
                            })
                    .create();
            Utils.updateData(context, mHistoryFileName, gson);
            Utils.updateData(context, mCurrentSessionFileName, gson);
        }

        mHistorySessionsLegacy.setFilename(null);
        ////////////////////////////
    }

    public void submitCurrentSession(Context context) {
        //TODO
        //Insert a copy of the current session in the second to last position. The "new" current session
        //will be at the last position.
        /*if (mAllSessions != null)
            mAllSessions.add(new Session(mCurrentSession));
        mCurrentSession.newSession();
        mCurrentSessionId = mCurrentSession.getId();
        PrefUtils.saveCurrentSessionIndex(this, context, mCurrentSessionId);*/
    }

    public Puzzle getPuzzle() {
        if (mPuzzle == null) {
            try {
                mPuzzle = PuzzlePlugins.getScramblers().get(mScrambler)
                        .cachedInstance();
            } catch (LazyInstantiatorException |
                    BadLazyClassDescriptionException | IOException e) {
                e.printStackTrace();
            }
        }
        return mPuzzle;
    }

    public Single<Session> getCurrentSessionDeferred(Context context) {
        return getSessionDeferred(context, mCurrentSessionId);
    }

    public Session getCurrentSession(Context context) throws CouchbaseLiteException, IOException {
        return getSession(context, mCurrentSessionId);
    }


    public Single<Session> getSessionDeferred(Context context, String id) {
        return Single.defer(() -> Single.just(getSession(context, id)));
    }

    public Session getSession(Context context, String id) throws CouchbaseLiteException, IOException {
        return fromDocId(context, id, Session.class);
    }

    public Observable<Session> getSessions(Context context) {
        return Observable.from(new ArrayList<>(mSessions))
                .subscribeOn(Schedulers.io())
                .flatMap(id -> {
                    try {
                        return Observable.just(getSession(context, id));
                    } catch (CouchbaseLiteException | IOException e) {
                        return Observable.error(e);
                    }
                });
    }

    public void resetCurrentSession() {
        //TODO
        /*mCurrentSession.newSession();*/
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PuzzleType && ((PuzzleType) o).getId().equals(mId);
    }

    @Override
    protected String getType() {
        return TYPE_PUZZLETYPE;
    }
}
