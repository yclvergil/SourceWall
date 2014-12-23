package com.example.sourcewall.fragment;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.sourcewall.LoginActivity;
import com.example.sourcewall.R;
import com.example.sourcewall.adapters.ChannelsAdapter;
import com.example.sourcewall.connection.ResultObject;
import com.example.sourcewall.connection.api.UserAPI;
import com.example.sourcewall.model.SubItem;
import com.example.sourcewall.model.UserInfo;
import com.example.sourcewall.util.Consts;
import com.example.sourcewall.util.ToastUtil;
import com.example.sourcewall.view.SubItemView;
import com.squareup.picasso.Picasso;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 * Created by NashLegend on 2014/9/15 0015.
 */
public class NavigationDrawerFragment extends Fragment implements View.OnClickListener {

    /**
     * Remember the position of the selected item.
     */
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";

    /**
     * Per the design guidelines, you should show the drawer on launch until the user manually
     * expands it. This shared preference tracks this.
     */
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";

    /**
     * Helper component that ties the action bar to the navigation drawer.
     */
    private ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;
    private View layoutView;
    private View mFragmentContainerView;

    private int mCurrentSelectedPosition = 0;
    private boolean mFromSavedInstanceState;
    private boolean mUserLearnedDrawer;
    private ExpandableListView listView;
    private ChannelsAdapter adapter;
    private View settingView;
    private View userView;
    private ImageView avatarView;
    private TextView userName;


    public NavigationDrawerFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);
        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            mFromSavedInstanceState = true;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        layoutView = inflater.inflate(
                R.layout.fragment_navigation_drawer, container, false);

        settingView = layoutView.findViewById(R.id.view_setting);
        userView = layoutView.findViewById(R.id.layout_user);
        avatarView = (ImageView) layoutView.findViewById(R.id.image_avatar);
        userName = (TextView) layoutView.findViewById(R.id.text_name);
        settingView.setOnClickListener(this);
        userView.setOnClickListener(this);

        listView = (ExpandableListView) layoutView.findViewById(R.id.list_channel);
        listView.setGroupIndicator(null);
        adapter = new ChannelsAdapter(getActivity());
        adapter.createDefaultChannels();
        listView.setAdapter(adapter);
        listView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                return false;
            }
        });
        listView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                if (v instanceof SubItemView) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.closeDrawer(mFragmentContainerView);
                    }
                    SubItem subItem = ((SubItemView) v).getSubItem();
                    Intent intent = new Intent();
                    intent.setAction(Consts.Action_Open_Content_Fragment);
                    intent.putExtra(Consts.Extra_SubItem, subItem);
                    getActivity().sendBroadcast(intent);
                }
                return false;
            }
        });
        return layoutView;
    }


    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    /**
     * Users of this fragment must call this method to set up the navigation drawer interactions.
     *
     * @param fragmentId   The android:id of this fragment in its activity's layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        mFragmentContainerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), mDrawerLayout, R.drawable.ic_drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }

                getActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) {
                    return;
                }
                if (!mUserLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to prevent auto-showing
                    // the navigation drawer automatically in the future.
                    mUserLearnedDrawer = true;
                    SharedPreferences sp = PreferenceManager
                            .getDefaultSharedPreferences(getActivity());
                    sp.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
                }
                getActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }
        };

        // If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
        // per the navigation drawer design guidelines.
        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        // Defer code dependent on restoration of previous instance state.
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        SubItem subItem = (SubItem) adapter.getChild(0, 0);
        Intent intent = new Intent();
        intent.setAction(Consts.Action_Open_Content_Fragment);
        intent.putExtra(Consts.Extra_SubItem, subItem);
        getActivity().sendBroadcast(intent);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        testLogin();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mDrawerLayout != null && isDrawerOpen()) {
            inflater.inflate(R.menu.global, menu);
            showGlobalContextActionBar();
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (item.getItemId() == R.id.action_example) {
            UserAPI.startLoginActivity(getActivity());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Per the navigation drawer design guidelines, updates the action bar to show the global app
     * 'context', rather than just what's in the current screen.
     */
    private void showGlobalContextActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.app_name);
    }

    private ActionBar getActionBar() {
        return ((ActionBarActivity) getActivity()).getSupportActionBar();
    }

    private final int Code_Login = 1033;

    private void onUserViewClicked() {
        if (!UserAPI.isLoggedIn()) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivityForResult(intent, Code_Login);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.layout_user:
                onUserViewClicked();
                break;
            case R.id.view_setting:

                break;
        }
    }

    private void testLogin() {
        TestLoginTask testLoginTask = new TestLoginTask();
        testLoginTask.execute();
    }

    private void loadUserInfo() {
        if (UserAPI.isLoggedIn()) {
            UserInfoTask task = new UserInfoTask();
            task.execute();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println(requestCode + " " + resultCode);
        if (requestCode == Code_Login && resultCode == Activity.RESULT_OK) {
            loadUserInfo();
        }
    }

    class TestLoginTask extends AsyncTask<Void, Integer, ResultObject> {

        @Override
        protected ResultObject doInBackground(Void... params) {
            return UserAPI.testLogin();
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            if (resultObject.ok) {
                loadUserInfo();
            } else {
                ToastUtil.toast("Not Logged In");
                switch (resultObject.code) {
                    case CODE_NOT_LOGGED_IN:
                        break;
                    case CODE_NETWORK_ERROR:
                        break;
                    case CODE_JSON_ERROR:
                        break;
                    case CODE_UNKNOWN:
                        break;
                }
            }
        }
    }

    class UserInfoTask extends AsyncTask<String, Intent, ResultObject> {

        @Override
        protected ResultObject doInBackground(String... params) {
            return UserAPI.getUserInfoByUkey(UserAPI.getUkey());
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            if (resultObject.ok) {
                setupUserInfo((UserInfo) resultObject.result);
            } else {
                ToastUtil.toast("Get UserInfo Failed");
            }
        }
    }

    private void setupUserInfo(UserInfo info) {
        Picasso.with(getActivity()).load(info.getAvatar())
                .resizeDimen(R.dimen.list_standard_comment_avatar_dimen, R.dimen.list_standard_comment_avatar_dimen)
                .into(avatarView);
        userName.setText(info.getNickname());
    }
}
