package net.nashlegend.sourcewall.fragment;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.umeng.analytics.MobclickAgent;

import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.adapters.NoticeAdapter;
import net.nashlegend.sourcewall.model.Notice;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.request.RequestObject;
import net.nashlegend.sourcewall.request.ResponseObject;
import net.nashlegend.sourcewall.request.api.MessageAPI;
import net.nashlegend.sourcewall.util.CommonUtil;
import net.nashlegend.sourcewall.util.Mob;
import net.nashlegend.sourcewall.util.UrlCheckUtil;
import net.nashlegend.sourcewall.view.NoticeView;
import net.nashlegend.sourcewall.view.common.LListView;
import net.nashlegend.sourcewall.view.common.LoadingView;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by NashLegend on 2015/2/12 0012
 */
public class NoticesFragment extends BaseFragment implements IChannelsFragment, LListView.OnRefreshListener, LoadingView.ReloadListener {

    @BindView(R.id.notice_list)
    LListView listView;
    @BindView(R.id.notice_loading_view)
    LoadingView loadingView;

    private NoticeAdapter adapter;
    private ProgressDialog progressDialog;
    private Unbinder unbinder;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notice_list, container, false);
        unbinder = ButterKnife.bind(this, view);
        loadingView.setReloadListener(this);
        adapter = new NoticeAdapter(getActivity());
        listView.setCanPullToRefresh(false);
        listView.setCanPullToLoadMore(false);
        listView.setAdapter(adapter);
        listView.setOnRefreshListener(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (CommonUtil.shouldThrottle()) {
                    return;
                }
                if (view instanceof NoticeView) {
                    //这里要做两个请求，但是可以直接请求notice地址，让系统主动删除请求 TODO
                    MobclickAgent.onEvent(getActivity(), Mob.Event_Open_One_Notice);
                    Notice notice = ((NoticeView) view).getData();
                    if (!UrlCheckUtil.redirectRequest(notice.getUrl(), notice.getId())) {
                        MessageAPI.ignoreOneNotice(notice.getId());
                    }
                }
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    RequestObject requestObject;

    private void loadData() {
        cancelPotentialTask();
        requestObject = MessageAPI.getNoticeList(new RequestObject.CallBack<ArrayList<Notice>>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<ArrayList<Notice>> result) {
                loadingView.onLoadFailed();
                toast(R.string.load_failed);
                listView.setCanPullToRefresh(true);
                listView.doneOperation();
            }

            @Override
            public void onSuccess(@NonNull ArrayList<Notice> result, @NonNull ResponseObject<ArrayList<Notice>> detailed) {
                loadingView.onLoadSuccess();
                if (result.size() == 0) {
                    toast(R.string.no_notice);
                }
                adapter.setList(result);
                adapter.notifyDataSetInvalidated();
                listView.setCanPullToRefresh(true);
                listView.doneOperation();
            }
        });
    }

    private void cancelPotentialTask() {
        if (requestObject != null) {
            requestObject.softCancel();
        }
        listView.doneOperation();
    }

    @Override
    public void onStartRefresh() {
        loadData();
    }

    @Override
    public void onStartLoadMore() {
        loadData();
    }

    @Override
    public void reload() {
        loadData();
    }

    @Override
    public int getFragmentMenu() {
        return R.menu.menu_notice_center;
    }

    @Override
    public boolean takeOverMenuInflate(MenuInflater inflater, Menu menu) {
        inflater.inflate(getFragmentMenu(), menu);
        return true;
    }

    @Override
    public boolean takeOverOptionsItemSelect(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_ignore_all:
                if (adapter.getCount() > 0) {
                    MobclickAgent.onEvent(getActivity(), Mob.Event_Ignore_All_Notice);
                    cancelPotentialTask();
                    ignoreAll();
                }
                break;
        }
        return true;
    }

    private void ignoreAll() {
        final RequestObject requestObject = MessageAPI.ignoreAllNotice(new RequestObject.CallBack<Boolean>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<Boolean> result) {
                CommonUtil.dismissDialog(progressDialog);
                toast("忽略未遂");
            }

            @Override
            public void onSuccess(@NonNull Boolean result, @NonNull ResponseObject<Boolean> detailed) {
                CommonUtil.dismissDialog(progressDialog);
                listView.setCanPullToRefresh(true);
                listView.doneOperation();
                adapter.clear();
                adapter.notifyDataSetInvalidated();
            }
        });

        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMessage(getString(R.string.message_wait_a_minute));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                requestObject.softCancel();
            }
        });
        progressDialog.show();
    }

    @Override
    public boolean takeOverBackPressed() {
        return false;
    }

    @Override
    public void resetData(SubItem subItem) {

    }

    @Override
    public void triggerRefresh() {

    }

    @Override
    public void prepareLoading(SubItem sub) {

    }

    @Override
    public void scrollToHead() {
        listView.setSelection(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
