package net.nashlegend.sourcewall.request.api;

import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.Author;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.request.JsonHandler;
import net.nashlegend.sourcewall.request.RequestBuilder;
import net.nashlegend.sourcewall.request.RequestObject;
import net.nashlegend.sourcewall.request.RequestObject.CallBack;
import net.nashlegend.sourcewall.request.ResponseObject;
import net.nashlegend.sourcewall.request.parsers.ArticleListParser;
import net.nashlegend.sourcewall.request.parsers.BooleanParser;
import net.nashlegend.sourcewall.request.parsers.ContentValueForKeyParser;
import net.nashlegend.sourcewall.request.parsers.DirectlyStringParser;
import net.nashlegend.sourcewall.request.parsers.Parser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

public class ArticleAPI extends APIBase {

    public ArticleAPI() {

    }

    /**
     * 返回文章列表
     *
     * @param type     文章类型
     * @param key      文章类型的key，如果是科学人首页则可以不取
     * @param offset
     * @param useCache 是否使用cache
     * @return
     */
    public static Observable<ResponseObject<ArrayList<Article>>> getArticleList(int type, String key, int offset, boolean useCache) {
        String url = "http://www.guokr.com/apis/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        switch (type) {
            case SubItem.Type_Collections:
                pairs.put("retrieve_type", "by_subject");
                break;
            case SubItem.Type_Subject_Channel:
                pairs.put("retrieve_type", "by_subject");
                pairs.put("subject_key", key);
                break;
            case SubItem.Type_Single_Channel:
                pairs.put("retrieve_type", "by_channel");
                pairs.put("channel_key", key);
                break;
        }
        pairs.put("limit", "20");
        pairs.put("offset", String.valueOf(offset));
        return new RequestBuilder<ArrayList<Article>>()
                .setUrl(url)
                .get()
                .setWithToken(false)
                .setParams(pairs)
                .useCacheFirst(useCache)
                .cacheTimeOut(300000)
                .setParser(new ArticleListParser())
                .requestObservable();
    }

    /**
     * 此处默认会取缓存，如果没有变动的话
     *
     * @param id
     * @return
     */
    public static Observable<ResponseObject<Article>> getArticleDetail(final String id) {
        String url = "http://www.guokr.com/article/" + id + "/";
        return new RequestBuilder<Article>()
                .setUrl(url)
                .useCacheIfFailed(true)
                .get()
                .setParser(new Parser<Article>() {
                    @Override
                    public Article parse(String response, ResponseObject<Article> responseObject) throws Exception {
                        Article article = Article.fromHtmlDetail(id, response);
                        responseObject.ok = true;
                        return article;
                    }
                })
                .requestObservable();
    }

    /**
     * 获取文章评论，json格式
     * resultObject.result是ArrayList[UComment]
     *
     * @param id     article ID
     * @param offset 从第几个开始加载
     * @param limit
     * @return ResponseObject
     */
    public static Observable<ResponseObject<ArrayList<UComment>>> getArticleReplies(final String id, final int offset, int limit) {
        String url = "http://apis.guokr.com/minisite/article_reply.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("article_id", id);
        pairs.put("limit", String.valueOf(limit));
        pairs.put("offset", String.valueOf(offset));
        return new RequestBuilder<ArrayList<UComment>>()
                .setUrl(url)
                .get()
                .setParams(pairs)
                .useCacheIfFailed(offset == 0)
                .setParser(new Parser<ArrayList<UComment>>() {
                    @Override
                    public ArrayList<UComment> parse(String response, ResponseObject<ArrayList<UComment>> responseObject) throws Exception {
                        ArrayList<UComment> list = new ArrayList<>();
                        JSONArray articles = JsonHandler.getUniversalJsonArray(response, responseObject);
                        if (articles != null) {
                            for (int i = 0; i < articles.length(); i++) {
                                JSONObject jo = articles.getJSONObject(i);
                                UComment comment = UComment.fromArticleJson(id, "", jo);
                                comment.setFloor((offset + i + 1) + "楼");
                                list.add(comment);
                            }
                        }
                        return list;
                    }
                })
                .requestObservable();
    }

    /**
     * 解析html获得文章热门评论
     *
     * @param hotElement 热门评论元素
     * @param aid        article ID
     * @return ResponseObject
     */
    private static ArrayList<UComment> getArticleHotComments(Element hotElement, String aid) throws Exception {
        ArrayList<UComment> list = new ArrayList<>();
        Elements comments = hotElement.getElementsByTag("li");
        if (comments != null && comments.size() > 0) {
            for (int i = 0; i < comments.size(); i++) {
                Element element = comments.get(i);
                UComment comment = new UComment();
                String id = element.id().replace("reply", "");
                Element tmp = element.select(".cmt-img").select(".cmtImg").select(".pt-pic").get(0);
                Author author = new Author();
                author.setName(tmp.getElementsByTag("a").get(0).attr("title"));
                author.setId(tmp.getElementsByTag("a").get(0).attr("href").replaceAll("\\D+", ""));
                author.setAvatar(tmp.getElementsByTag("img").get(0).attr("src").replaceAll("\\?.*$", ""));
                String likeNum = element.getElementsByClass("cmt-do-num").get(0).text();
                String date = element.getElementsByClass("cmt-info").get(0).text();
                String content = element.select(".cmt-content").select(".gbbcode-content").select(".cmtContent").get(0).outerHtml();
                Elements tmpElements = element.getElementsByClass("cmt-auth");
                if (tmpElements != null && tmpElements.size() > 0) {
                    author.setTitle(element.getElementsByClass("cmt-auth").get(0).attr("title"));
                }

                comment.setID(id);
                comment.setLikeNum(Integer.valueOf(likeNum));
                comment.setAuthor(author);
                comment.setDate(date);
                comment.setContent(content);
                comment.setHostID(aid);
                list.add(comment);
            }
        }
        return list;
    }

    /**
     * 获取一条article的简介，也就是除了正文之外的一切，这里只需要两个，id和title
     *
     * @param article_id article_id
     * @return ResponseObject
     */
    private static Observable<ResponseObject<Article>> getArticleSimpleByID(String article_id) {
        String url = "http://apis.guokr.com/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("article_id", article_id);
        return new RequestBuilder<Article>()
                .setUrl(url)
                .get()
                .setParams(pairs)
                .setParser(new Parser<Article>() {
                    @Override
                    public Article parse(String str, ResponseObject<Article> responseObject) throws Exception {
                        JSONArray articlesArray = JsonHandler.getUniversalJsonArray(str, responseObject);
                        assert articlesArray != null;
                        JSONObject articleObject = articlesArray.getJSONObject(0);
                        return Article.fromJsonSimple(articleObject);
                    }
                })
                .requestObservable();
    }

    public static Observable<UComment> getSingleComment(String url) {
        return new RequestBuilder<String>()
                .setUrl(url)
                .get()
                .setWithToken(false)
                .setParser(new DirectlyStringParser())
                .requestObservable()
                .flatMap(new Func1<ResponseObject<String>, Observable<UComment>>() {
                    @Override
                    public Observable<UComment> call(ResponseObject<String> response) {
                        Document document = Jsoup.parse(response.result);
                        Elements elements = document.getElementsByTag("a");
                        if (elements.size() == 1) {
                            Matcher matcher = Pattern.compile("^/article/(\\d+)/.*#reply(\\d+)$").matcher(elements.get(0).text());
                            if (matcher.find()) {
                                String article_id = matcher.group(1);
                                String reply_id = matcher.group(2);
                                return Observable.zip(getArticleSimpleByID(article_id), getSingleCommentByID(reply_id),
                                        new Func2<ResponseObject<Article>, ResponseObject<UComment>, UComment>() {
                                            @Override
                                            public UComment call(ResponseObject<Article> article, ResponseObject<UComment> comment) {
                                                if (article.ok && comment.ok) {
                                                    UComment uComment = comment.result;
                                                    uComment.setHostID(article.result.getId());
                                                    uComment.setHostTitle(article.result.getTitle());
                                                    return uComment;
                                                }
                                                return null;
                                            }
                                        });
                            }
                        }
                        return Observable.error(new IllegalStateException("not a correct redirect content"));
                    }
                })
                .flatMap(new Func1<UComment, Observable<UComment>>() {
                    @Override
                    public Observable<UComment> call(UComment uComment) {
                        if (uComment != null) {
                            return Observable.just(uComment);
                        }
                        return Observable.error(new IllegalStateException("not a correct redirect content"));
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    /**
     * 根据一条通知的id获取所有内容，蛋疼的需要跳转。
     * 先是：http://www.guokr.com/user/notice/8738252/
     * 跳到：http://www.guokr.com/article/reply/123456/
     * 这就走到了类似getSingleCommentFromRedirectUrl这一步
     * 两次跳转后可获得article_id，但是仍然无法获得title
     * 还需要另一个接口获取article的摘要。getArticleSimpleByID(article_id)
     *
     * @param notice_id 通知id
     * @return resultObject resultObject.result是UComment
     */
    public static Observable<UComment> getSingleCommentByNoticeID(String notice_id) {
        String notice_url = "http://www.guokr.com/user/notice/" + notice_id + "/";
        return getSingleComment(notice_url);
    }

    public static Observable<UComment> getSingleCommentFromRedirectUrl(String reply_url) {
        return getSingleComment(reply_url);
    }

    /**
     * 根据一条评论的id获取评论内容，主要应用于消息通知
     * 无法取得此评论的文章的id和标题，无法取得楼层。
     *
     * @param reply_id 评论id
     * @return resultObject resultObject.result是UComment
     */
    public static Observable<ResponseObject<UComment>> getSingleCommentByID(String reply_id) {
        String url = "http://apis.guokr.com/minisite/article_reply/" + reply_id + ".json";
        return new RequestBuilder<UComment>()
                .setUrl(url)
                .get()
                .setParser(new Parser<UComment>() {
                    @Override
                    public UComment parse(String str, ResponseObject<UComment> responseObject) throws Exception {
                        JSONObject replyObject = JsonHandler.getUniversalJsonObject(str, responseObject);
                        assert replyObject != null;
                        return UComment.fromArticleJson(replyObject);
                    }
                })
                .requestObservable();
    }

    /**
     * 推荐文章
     *
     * @param articleID article ID
     * @param title     文章标题
     * @param summary   文章总结
     * @param comment   推荐评语
     * @return ResponseObject
     */
    public static RequestObject<Boolean> recommendArticle(String articleID, String title, String summary, String comment, CallBack<Boolean> callBack) {
        String articleUrl = "http://www.guokr.com/article/" + articleID + "/";
        return UserAPI.recommendLink(articleUrl, title, summary, comment, callBack);
    }

    /**
     * 赞一个文章评论
     *
     * @param id 文章id
     * @return ResponseObject
     */
    public static RequestObject<Boolean> likeComment(String id, CallBack<Boolean> callBack) {
        String url = "http://www.guokr.com/apis/minisite/article_reply_liking.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("reply_id", id);
        return new RequestBuilder<Boolean>()
                .setUrl(url)
                .setParser(new BooleanParser())
                .setRequestCallBack(callBack)
                .setParams(pairs)
                .post()
                .startRequest();
    }

    /**
     * 删除我的评论
     *
     * @param id 评论id
     * @return ResponseObject
     */
    public static RequestObject<Boolean> deleteMyComment(String id, CallBack<Boolean> callBack) {
        String url = "http://www.guokr.com/apis/minisite/article_reply.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("reply_id", id);
        return new RequestBuilder<Boolean>()
                .setUrl(url)
                .setParser(new BooleanParser())
                .setRequestCallBack(callBack)
                .setParams(pairs)
                .delete()
                .startRequest();
    }

    /**
     * 使用json请求回复文章
     *
     * @param id      文章id
     * @param content 回复内容
     * @return ResponseObject.result is the reply_id if ok;
     */
    public static RequestObject<String> replyArticle(String id, String content, CallBack<String> callBack) {
        String url = "http://apis.guokr.com/minisite/article_reply.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("article_id", id);
        pairs.put("content", content);
        return new RequestBuilder<String>()
                .setUrl(url)
                .setParser(new ContentValueForKeyParser("id"))
                .setRequestCallBack(callBack)
                .setParams(pairs)
                .post()
                .startRequest();
    }
}
