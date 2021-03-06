package net.nashlegend.sourcewall.request.api;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.Post;
import net.nashlegend.sourcewall.model.Question;
import net.nashlegend.sourcewall.request.HttpUtil;
import net.nashlegend.sourcewall.request.JsonHandler;
import net.nashlegend.sourcewall.request.RequestBuilder;
import net.nashlegend.sourcewall.request.RequestObject;
import net.nashlegend.sourcewall.request.RequestObject.CallBack;
import net.nashlegend.sourcewall.request.ResponseObject;
import net.nashlegend.sourcewall.request.parsers.Parser;
import net.nashlegend.sourcewall.util.Config;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class APIBase {

    /**
     * 统一回复，回复主题站、帖子、问题
     *
     * @return ResponseObject
     */
    @Nullable
    public static RequestObject<String> reply(AceModel data, String content, CallBack<String> callBack) {
        if (data instanceof Article) {
            return ArticleAPI.replyArticle(((Article) data).getId(), content + Config.getSimpleReplyTail(), callBack);
        } else if (data instanceof Post) {
            return PostAPI.replyPost(((Post) data).getId(), content + Config.getSimpleReplyTail(), callBack);
        } else if (data instanceof Question) {
            return QuestionAPI.answerQuestion(((Question) data).getId(), content + Config.getSimpleReplyTail(), callBack);
        } else {
            return null;
        }
    }

    /**
     * 上传图片
     *
     * @param path 要上传图片的路径
     * @return 返回ResponseObject，resultObject.result是上传后的图片地址，果壳并不会对图片进行压缩
     */
    public static void uploadImage(String path, CallBack<String> callBack) {
        // TODO: 16/5/6 未压缩
        new RequestBuilder<String>()
                .setUrl("http://www.guokr.com/apis/image.json?enable_watermark=true")
                .upload(path)
                .setUploadParamKey("upload_file")
                .setMediaType("image/*")
                .setRequestCallBack(callBack)
                .setParser(new Parser<String>() {
                    @Override
                    public String parse(String str, ResponseObject<String> responseObject) throws Exception {
                        JSONObject object = JsonHandler.getUniversalJsonObject(str, responseObject);
                        if (object != null) {
                            return object.getString("url");
                        } else {
                            responseObject.ok = false;
                            return "";
                        }
                    }
                })
                .startRequest();
    }

    /**
     * 使用github的接口转换markdown为html.
     * 通过github接口转换markdown，一小时只能60次
     *
     * @param text 要转换的文本内容
     * @return ResponseObject
     */
    @Deprecated
    public static ResponseObject<String> parseMarkdownByGitHub(String text) {
        ResponseObject<String> resultObject = new ResponseObject<>();
        if (TextUtils.isEmpty(text)) {
            resultObject.ok = true;
            resultObject.result = "";
        } else {
            String url = "https://api.github.com/markdown";
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("text", text);
                jsonObject.put("mode", "gfm");
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(JSON, jsonObject.toString());
                Request request = new Request.Builder().post(body).url(url).build();
                Response response = HttpUtil.getDefaultHttpClient().newCall(request).execute();
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    resultObject.ok = true;
                    resultObject.result = result;
                }
            } catch (Exception e) {
                JsonHandler.handleRequestException(e, resultObject);
            }
        }
        return resultObject;
    }

    /**
     * 将时间转换成可见的。话说果壳返回的时间格式是什么标准
     *
     * @param dateString 传入的时间字符串
     * @return 解析后的时间 yyyy-mm-dd hh:mm:ss
     */
    @SuppressLint("SimpleDateFormat")
    public static String parseDate(String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = dateString.replace("T", " ").replaceAll("[\\+\\.]\\S+$", "");
        try {
            Date date = sdf.parse(time);
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime(date);
            GregorianCalendar now = new GregorianCalendar();
            int diff;
            if (now.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
                diff = calendar.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR);
            } else {
                int yearDiff = calendar.get(Calendar.YEAR) - now.get(Calendar.YEAR);
                if (yearDiff == 1) {
                    int max = now.isLeapYear(now.get(Calendar.YEAR)) ? 366 : 365;
                    diff = calendar.get(Calendar.DAY_OF_YEAR) + max - now.get(Calendar.DAY_OF_YEAR);
                } else if (yearDiff == -1) {
                    int max = calendar.isLeapYear(calendar.get(Calendar.YEAR)) ? 366 : 365;
                    diff = calendar.get(Calendar.DAY_OF_YEAR) - max - now.get(Calendar.DAY_OF_YEAR);
                } else {
                    diff = (int) (calendar.getTimeInMillis() / 86400000 - new GregorianCalendar().getTimeInMillis() / 86400000);
                }
            }
            if (diff < -1 || diff > 0) {
                sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            } else if (diff == -1) {
                sdf = new SimpleDateFormat("昨天HH:mm");
            } else if (diff == 0) {
                sdf = new SimpleDateFormat("今天HH:mm");
            }
            time = sdf.format(date.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return time;
    }
}
