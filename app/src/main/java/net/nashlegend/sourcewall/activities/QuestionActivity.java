package net.nashlegend.sourcewall.activities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.umeng.analytics.MobclickAgent;

import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.adapters.QuestionDetailAdapter;
import net.nashlegend.sourcewall.dialogs.FavorDialog;
import net.nashlegend.sourcewall.dialogs.InputDialog;
import net.nashlegend.sourcewall.model.Question;
import net.nashlegend.sourcewall.model.QuestionAnswer;
import net.nashlegend.sourcewall.request.RequestObject;
import net.nashlegend.sourcewall.request.ResponseObject;
import net.nashlegend.sourcewall.request.api.MessageAPI;
import net.nashlegend.sourcewall.request.api.QuestionAPI;
import net.nashlegend.sourcewall.request.api.UserAPI;
import net.nashlegend.sourcewall.util.AutoHideUtil;
import net.nashlegend.sourcewall.util.AutoHideUtil.AutoHideListener;
import net.nashlegend.sourcewall.util.CommonUtil;
import net.nashlegend.sourcewall.util.Consts;
import net.nashlegend.sourcewall.util.Mob;
import net.nashlegend.sourcewall.util.ShareUtil;
import net.nashlegend.sourcewall.util.UrlCheckUtil;
import net.nashlegend.sourcewall.view.AnswerListItemView;
import net.nashlegend.sourcewall.view.common.LListView;
import net.nashlegend.sourcewall.view.common.LoadingView;

import java.lang.reflect.Method;
import java.util.ArrayList;

import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;


public class QuestionActivity extends BaseActivity implements LListView.OnRefreshListener, View.OnClickListener, LoadingView.ReloadListener {

    private LListView listView;
    private QuestionDetailAdapter adapter;
    private Question question;
    private LoadingView loadingView;
    private String notice_id;
    private FloatingActionsMenu floatingActionsMenu;
    private ProgressBar progressBar;
    private AppBarLayout appbar;
    private int headerHeight = 112;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        MobclickAgent.onEvent(this, Mob.Event_Open_Question);
        loadingView = (LoadingView) findViewById(R.id.question_progress_loading);
        loadingView.setReloadListener(this);
        progressBar = (ProgressBar) findViewById(R.id.question_loading);
        appbar = (AppBarLayout) findViewById(R.id.app_bar);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setOnClickListener(new View.OnClickListener() {

            boolean preparingToScrollToHead = false;

            @Override
            public void onClick(View v) {
                if (preparingToScrollToHead) {
                    listView.setSelection(0);
                } else {
                    preparingToScrollToHead = true;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            preparingToScrollToHead = false;
                        }
                    }, 200);
                }
            }
        });
        question = getIntent().getParcelableExtra(Consts.Extra_Question);
        notice_id = getIntent().getStringExtra(Consts.Extra_Notice_Id);
        listView = (LListView) findViewById(R.id.list_detail);
        adapter = new QuestionDetailAdapter(this);
        listView.setAdapter(adapter);

        listView.setCanPullToLoadMore(false);
        listView.setOnRefreshListener(this);
        listView.setOnItemClickListener(onItemClickListener);

        floatingActionsMenu = (FloatingActionsMenu) findViewById(R.id.layout_operation);
        FloatingActionButton replyButton = (FloatingActionButton) findViewById(R.id.button_reply);
        FloatingActionButton recomButton = (FloatingActionButton) findViewById(R.id.button_recommend);
        FloatingActionButton favorButton = (FloatingActionButton) findViewById(R.id.button_favor);

        replyButton.setOnClickListener(this);
        recomButton.setOnClickListener(this);
        favorButton.setOnClickListener(this);

        headerHeight = (int) getResources().getDimension(R.dimen.actionbar_height);
        AutoHideUtil.applyListViewAutoHide(this, listView, (int) getResources().getDimension(R.dimen.actionbar_height), autoHideListener);
        floatingActionsMenu.setVisibility(View.GONE);
        loadData(-1);
    }

    private void loadData(int offset) {
        if (offset < 0) {
            loadFromQuestion();
        } else {
            loadAnswers(offset);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_question, menu);
        try {
            Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
            m.setAccessible(true);
            m.invoke(menu, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!UserAPI.isLoggedIn()) {
            menu.findItem(R.id.action_follow_question).setVisible(false);
            menu.findItem(R.id.action_unfollow_question).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_follow_question:
                followQuestion();
                break;
            case R.id.action_unfollow_question:
                unfollowQuestion();
                break;
            case R.id.action_open_in_browser:
                if (!TextUtils.isEmpty(question.getUrl())) {
                    MobclickAgent.onEvent(this, Mob.Event_Open_Question_In_Browser);
                    UrlCheckUtil.openWithBrowser(question.getUrl());
                }
                break;
            case R.id.action_share_to_wechat_circle:
                MobclickAgent.onEvent(this, Mob.Event_Share_Question_To_Wechat_Circle);
                ShareUtil.shareToWeiXinCircle(this, question.getUrl(), question.getTitle(), question.getSummary(), null);
                break;
            case R.id.action_share_to_wechat_friends:
                MobclickAgent.onEvent(this, Mob.Event_Share_Question_To_Wechat_friend);
                ShareUtil.shareToWeiXinFriends(this, question.getUrl(), question.getTitle(), question.getSummary(), null);
                break;
            case R.id.action_share_to_weibo:
                MobclickAgent.onEvent(this, Mob.Event_Share_Question_To_Weibo);
                ShareUtil.shareToWeibo(this, question.getUrl(), question.getTitle(), question.getSummary(), null);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStartRefresh() {
        loadData(-1);
    }

    @Override
    public void onStartLoadMore() {
        loadData(adapter.getCount() - 1);
    }

    AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            onReplyItemClick(view);
        }
    };

    private void onReplyItemClick(final View view) {
        if (CommonUtil.shouldThrottle()) {
            return;
        }
        if (view instanceof AnswerListItemView) {
            Intent intent = new Intent(this, AnswerActivity.class);
            intent.putExtra(Consts.Extra_Answer, ((AnswerListItemView) view).getData());
            intent.putExtra(Consts.Extra_Question, question);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, 0);
        }
    }

    @Override
    public void onClick(View v) {
        if (CommonUtil.shouldThrottle()) {
            return;
        }
        switch (v.getId()) {
            case R.id.button_reply:
                answerQuestion();
                break;
            case R.id.button_recommend:
                recommend();
                break;
            case R.id.button_favor:
                favor();
                break;
        }
    }

    private void favor() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            MobclickAgent.onEvent(this, Mob.Event_Favor_Question);
            new FavorDialog.Builder(this).setTitle(R.string.action_favor).create(question).show();
        }
    }

    private void recommend() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            MobclickAgent.onEvent(this, Mob.Event_Recommend_Question);
            InputDialog.Builder builder = new InputDialog.Builder(this);
            builder.setTitle(R.string.recommend_question);
            builder.setCancelable(true);
            builder.setCanceledOnTouchOutside(false);
            builder.setOnClickListener(new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        InputDialog d = (InputDialog) dialog;
                        confirmRecommend(d.InputString);
                    }
                }
            });
            InputDialog inputDialog = builder.create();
            inputDialog.show();
        }
    }

    private void confirmRecommend(String comment) {
        QuestionAPI.recommendQuestion(question.getId(), question.getTitle(), question.getSummary(), comment, new RequestObject.CallBack<Boolean>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<Boolean> result) {
                toast(R.string.recommend_failed);
            }

            @Override
            public void onSuccess(@NonNull Boolean result, @NonNull ResponseObject<Boolean> detailed) {
                toast(R.string.recommend_ok);
            }
        });
    }

    private void answerQuestion() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            Intent intent = new Intent(this, ReplyActivity.class);
            intent.putExtra(Consts.Extra_Ace_Model, question);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, 0);
        }
    }

    @Override
    public void reload() {
        loadData(-1);
    }


    private void followQuestion() {
        QuestionAPI.followQuestion(question.getId(), new RequestObject.CallBack<Boolean>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<Boolean> result) {
                toast(R.string.follow_failed);
            }

            @Override
            public void onSuccess(@NonNull Boolean result, @NonNull ResponseObject<Boolean> detailed) {
                toast(R.string.follow_ok);
            }
        });
    }

    private void unfollowQuestion() {
        MobclickAgent.onEvent(QuestionActivity.this, Mob.Event_Unfollow_Question);
        QuestionAPI.unfollowQuestion(question.getId(), new RequestObject.CallBack<Boolean>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<Boolean> result) {
                toast(R.string.unfollow_failed);
            }

            @Override
            public void onSuccess(@NonNull Boolean result, @NonNull ResponseObject<Boolean> detailed) {
                toast(R.string.unfollow_ok);
            }
        });
    }

    private AutoHideListener autoHideListener = new AutoHideListener() {
        AnimatorSet backAnimatorSet;
        AnimatorSet hideAnimatorSet;

        @Override
        public void animateHide() {
            if (backAnimatorSet != null && backAnimatorSet.isRunning()) {
                backAnimatorSet.cancel();
            }
            if (hideAnimatorSet == null || !hideAnimatorSet.isRunning()) {
                hideAnimatorSet = new AnimatorSet();
                ObjectAnimator headerAnimator = ObjectAnimator.ofFloat(appbar, "translationY", appbar.getTranslationY(), -headerHeight);
                ObjectAnimator header2Animator = ObjectAnimator.ofFloat(progressBar, "translationY", progressBar.getTranslationY(), -headerHeight);
                ObjectAnimator footerAnimator = ObjectAnimator.ofFloat(floatingActionsMenu, "translationY", floatingActionsMenu.getTranslationY(), floatingActionsMenu.getHeight());
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(headerAnimator);
                animators.add(header2Animator);
                animators.add(footerAnimator);
                hideAnimatorSet.setDuration(300);
                hideAnimatorSet.playTogether(animators);
                hideAnimatorSet.start();
            }
        }

        @Override
        public void animateBack() {
            if (hideAnimatorSet != null && hideAnimatorSet.isRunning()) {
                hideAnimatorSet.cancel();
            }
            if (backAnimatorSet == null || !backAnimatorSet.isRunning()) {
                backAnimatorSet = new AnimatorSet();
                ObjectAnimator headerAnimator = ObjectAnimator.ofFloat(appbar, "translationY", appbar.getTranslationY(), 0f);
                ObjectAnimator header2Animator = ObjectAnimator.ofFloat(progressBar, "translationY", progressBar.getTranslationY(), 0f);
                ObjectAnimator footerAnimator = ObjectAnimator.ofFloat(floatingActionsMenu, "translationY", floatingActionsMenu.getTranslationY(), 0f);
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(headerAnimator);
                animators.add(header2Animator);
                animators.add(footerAnimator);
                backAnimatorSet.setDuration(300);
                backAnimatorSet.playTogether(animators);
                backAnimatorSet.start();
            }
        }
    };

    private void loadFromQuestion() {
        if (!TextUtils.isEmpty(notice_id)) {
            MessageAPI.ignoreOneNotice(notice_id);
            notice_id = null;
        }
        QuestionAPI
                .getQuestionDetailByID(question.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ResponseObject<Question>>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(ResponseObject<Question> result) {
                        if (result.ok) {
                            progressBar.setVisibility(View.VISIBLE);
                            floatingActionsMenu.setVisibility(View.VISIBLE);
                            loadingView.onLoadSuccess();
                            question = result.result;
                            adapter.add(0, question);
                            adapter.notifyDataSetChanged();
                            loadAnswers(0);
                        } else {
                            loadingView.onLoadFailed();
                        }
                    }
                });
    }

    private void loadAnswers(int offset) {
        QuestionAPI
                .getQuestionAnswers(question.getId(), offset)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        loadingView.onLoadSuccess();
                        listView.doneOperation();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ResponseObject<ArrayList<QuestionAnswer>>>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(ResponseObject<ArrayList<QuestionAnswer>> result) {
                        progressBar.setVisibility(View.GONE);
                        if (result.ok) {
                            loadingView.onLoadSuccess();
                            ArrayList<QuestionAnswer> ars = result.result;
                            if (ars.size() > 0) {
                                adapter.addAll(ars);
                                adapter.notifyDataSetChanged();
                            }
                        } else {
                            if (result.statusCode == 404) {
                                toastSingleton(R.string.page_404);
                                finish();
                            } else {
                                toastSingleton(getString(R.string.load_failed));
                                loadingView.onLoadFailed();
                            }
                        }
                        if (adapter.getCount() > 0) {
                            listView.setCanPullToLoadMore(true);
                        } else {
                            listView.setCanPullToLoadMore(false);
                        }
                        listView.doneOperation();
                    }
                });
    }
}
