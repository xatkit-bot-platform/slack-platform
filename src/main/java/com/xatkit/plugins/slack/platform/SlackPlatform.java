package com.xatkit.plugins.slack.platform;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.request.auth.AuthTestRequest;
import com.github.seratch.jslack.api.methods.request.conversations.ConversationsListRequest;
import com.github.seratch.jslack.api.methods.request.oauth.OAuthAccessRequest;
import com.github.seratch.jslack.api.methods.request.users.UsersInfoRequest;
import com.github.seratch.jslack.api.methods.request.users.UsersListRequest;
import com.github.seratch.jslack.api.methods.response.auth.AuthTestResponse;
import com.github.seratch.jslack.api.methods.response.conversations.ConversationsListResponse;
import com.github.seratch.jslack.api.methods.response.oauth.OAuthAccessResponse;
import com.github.seratch.jslack.api.methods.response.users.UsersInfoResponse;
import com.github.seratch.jslack.api.methods.response.users.UsersListResponse;
import com.github.seratch.jslack.api.model.Attachment;
import com.github.seratch.jslack.api.model.Conversation;
import com.github.seratch.jslack.api.model.ConversationType;
import com.github.seratch.jslack.api.model.User;
import com.github.seratch.jslack.api.model.block.LayoutBlock;
import com.google.gson.JsonObject;
import com.xatkit.core.XatkitBot;
import com.xatkit.core.XatkitException;
import com.xatkit.core.platform.RuntimePlatform;
import com.xatkit.core.platform.action.RuntimeActionResult;
import com.xatkit.core.server.HttpMethod;
import com.xatkit.core.server.HttpUtils;
import com.xatkit.core.server.RestHandlerFactory;
import com.xatkit.execution.StateContext;
import com.xatkit.plugins.chat.platform.ChatPlatform;
import com.xatkit.plugins.chat.platform.io.ChatIntentProvider;
import com.xatkit.plugins.slack.SlackUtils;
import com.xatkit.plugins.slack.platform.action.EnumerateList;
import com.xatkit.plugins.slack.platform.action.IsOnline;
import com.xatkit.plugins.slack.platform.action.ItemizeList;
import com.xatkit.plugins.slack.platform.action.PostAttachmentsMessage;
import com.xatkit.plugins.slack.platform.action.PostFileMessage;
import com.xatkit.plugins.slack.platform.action.PostLayoutBlocksMessage;
import com.xatkit.plugins.slack.platform.action.PostMessage;
import com.xatkit.plugins.slack.platform.action.Reply;
import com.xatkit.plugins.slack.platform.action.ReplyAttachmentsMessage;
import com.xatkit.plugins.slack.platform.action.ReplyFileMessage;
import com.xatkit.plugins.slack.platform.action.ReplyLayoutBlocksMessage;
import com.xatkit.plugins.slack.platform.io.SlackIntentProvider;
import fr.inria.atlanmod.commons.log.Log;
import lombok.NonNull;
import org.apache.commons.configuration2.Configuration;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.xatkit.plugins.slack.util.SlackUtils.logSlackApiResponse;
import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A {@link RuntimePlatform} class that connects and interacts with the Slack API.
 */
public class SlackPlatform extends ChatPlatform {

    /**
     * The {@code clientId} of the Slack app associated to the deployed bot.
     * <p>
     * <b>Note</b>: the {@code clientId} is provided in the {@link Configuration} (using the key
     * {@link SlackUtils#SLACK_CLIENT_ID_KEY}) for <i>distributed </i> applications. Applications that are currently
     * under development can specify the Slack {@code token} corresponding to their application using the
     * {@link SlackUtils#SLACK_TOKEN_KEY} configuration key.
     * <p>
     * If a {@code clientId} is provided the {@link Configuration} must also contain a {@code clientSecret}.
     *
     * @see #clientSecret
     */
    private String clientId;

    /**
     * The {@code clientSecret} of the Slack app associated to the deployed bot.
     * <p>
     * <b>Note</b>: the {@code clientSecret} is provided in the {@link Configuration} (using the key
     * {@link SlackUtils#SLACK_CLIENT_SECRET_KEY}) for <i>distributed</i> applications. Applications that are
     * currently under development can specify the Slack {@code token} corresponding to their application using the
     * {@link SlackUtils#SLACK_TOKEN_KEY} configuration key.
     * <p>
     * If a {@code clientSecret} is provided the {@link Configuration} must also contain a {@code clientId}.
     *
     * @see #clientId
     */
    private String clientSecret;


    /**
     * The {@link Slack} API client used to post messages.
     */
    private Slack slack;

    /**
     * A {@link Map} containing the {@code name -> ID} mapping of all the channels in the workspaces where the bot is
     * installed.
     * <p>
     * This {@link Map} contains entries for conversation name, user name, user display name, and IDs, allowing fast
     * lookups to retrieve a channel identifier from a given name.
     * <p>
     * Keys in this {@link Map} are {@code teamId}s.
     * <p>
     * This {@link Map} is populated by {@link #loadChannels(String)}.
     *
     * @see #loadChannels(String)
     */
    private Map<String, Map<String, String>> channelNames;

    /**
     * A {@link Map} containing the IDs of all the group channels for all the workspaces where the bot is installed.
     * <p>
     * A group channel corresponds to any conversation that can be joined by multiple users (typically everything
     * excepted user channels).
     * <p>
     * Keys in this {@link Map} are {@code teamId}s.
     * <p>
     * This {@link Map} is populated by {@link #loadChannels(String)}.
     *
     * @see #loadChannels(String)
     */
    private Map<String, List<String>> groupChannels;

    /**
     * A {@link Map} containing the IDs of all the user channels for all the workspaces where the bot is installed.
     * <p>
     * A user channel is a direct message channel between an user and the bot.
     * <p>
     * Keys in this {@link Map} are {@code teamId}s.
     * <p>
     * This {@link Map} is populated by {@link #loadChannels(String)}.
     *
     * @see #loadChannels(String)
     */
    private Map<String, List<String>> userChannels;

    /**
     * A {@link Map} containins the Slack {@code tokens} associated to the workspace's {@code teamId}s.
     * <p>
     * The {@code teamId} value corresponds to the identifier of the workspaces where the app is installed. This
     * identifier is used in {@code Reply*} action to target specific workspaces.
     */
    private Map<String, String> teamIdToSlackToken;

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatIntentProvider<? extends ChatPlatform> getChatIntentProvider() {
        return this.getSlackIntentProvider();
    }

    /**
     * Initializes and returns a new {@link SlackIntentProvider}.
     *
     * @return the {@link SlackIntentProvider}
     */
    public SlackIntentProvider getSlackIntentProvider() {
        return new SlackIntentProvider(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method initializes the underlying {@link Slack} client. If the provided {@code configuration} contains a
     * Slack {@code token} (using the key {@link SlackUtils#SLACK_TOKEN_KEY} the constructor initializes the platform
     * with the provided token, and does not allow additional installation of the Slack app. This feature is
     * typically used in development mode to quickly test the bot under development in a single Slack workspace.
     * <p>
     * If the {@code configuration} contains a Slack {@code clientId} and {@code clientSecret} the platform starts a
     * dedicated REST handler that will receive OAuth queries when the Slack app is installed by clients. This
     * handler manages internal caches to ensure that {@code Reply*} actions are correctly sent to the appropriate
     * workspace.
     *
     * @see SlackUtils#SLACK_TOKEN_KEY
     * @see SlackUtils#SLACK_CLIENT_ID_KEY
     * @see SlackUtils#SLACK_CLIENT_SECRET_KEY
     */
    @Override
    public void start(XatkitBot xatkitBot, Configuration configuration) {
        super.start(xatkitBot, configuration);
        this.teamIdToSlackToken = new HashMap<>();
        slack = new Slack();
        this.channelNames = new HashMap<>();
        this.groupChannels = new HashMap<>();
        this.userChannels = new HashMap<>();
        String slackToken = configuration.getString(SlackUtils.SLACK_TOKEN_KEY);
        if (nonNull(slackToken)) {
            AuthTestRequest request = AuthTestRequest.builder().token(slackToken).build();
            try {
                AuthTestResponse response = slack.methods().authTest(request);
                logSlackApiResponse(response);
                String teamId = response.getTeamId();
                teamIdToSlackToken.put(teamId, slackToken);
                this.loadChannels(teamId);
                this.notifyNewInstallation(teamId, slackToken);
            } catch (IOException | SlackApiException e) {
                throw new XatkitException("Cannot retrieve the team associated to the provided Slack token", e);
            }
        } else {
            Log.info("The configuration does not contain a Slack token, starting {0} in OAuth mode",
                    SlackPlatform.class.getSimpleName());
            clientId = configuration.getString(SlackUtils.SLACK_CLIENT_ID_KEY);
            checkArgument(nonNull(clientId) && !clientId.isEmpty(), "Cannot construct a %s from the provided clientId" +
                            " %s, please ensure that Xatkit configuration contains a valid clientId associated to the" +
                            " key %s"
                    , SlackPlatform.class.getSimpleName(), clientId, SlackUtils.SLACK_CLIENT_ID_KEY);
            clientSecret = configuration.getString(SlackUtils.SLACK_CLIENT_SECRET_KEY);
            checkArgument(nonNull(clientSecret) && !clientSecret.isEmpty(), "Cannot construct a %s from the provided " +
                            "clientSecret %s, please ensure that Xatkit configuration contains a valid clientSecret " +
                            "associated to the key %s", SlackPlatform.class.getSimpleName(), clientSecret,
                    SlackUtils.SLACK_CLIENT_SECRET_KEY);
            registerOAuthRestHandler();
        }
    }

    /**
     * Formats the provided {@code list} into an enumeration.
     * <p>
     * This method accepts any {@link List} and relies on the {@code toString} implementation of its elements.
     *
     * @param context the current {@link StateContext}
     * @param list    the {@link List} to format
     * @return the enumeration formatted as a string
     */
    public @NonNull String enumerateList(@NonNull StateContext context, @NonNull List<?> list) {
        EnumerateList action = new EnumerateList(this, context, list);
        RuntimeActionResult result = action.call();
        return (String) result.getResult();
    }

    /**
     * Returns whether the given {@code username} in the provided {@code teamId} is online.
     * <p>
     * The provided {@code username} can be a user ID, name, or real name.
     *
     * @param context  the current {@link StateContext}
     * @param username the user ID, name, or real name to check
     * @param teamId   the identifier of the Slack workspace containing the user to check
     * @return {@code true} if the user is online, {@code false} otherwise
     */
    public boolean isOnline(@NonNull StateContext context, @NonNull String username, @NonNull String teamId) {
        IsOnline action = new IsOnline(this, context, username, teamId);
        RuntimeActionResult result = action.call();
        return (Boolean) result.getResult();
    }

    /**
     * Formats the provided {@code list} into an item list.
     * <p>
     * This method accepts any {@link List} and relies on the {@code toString} implementation of its elements.
     *
     * @param context the current {@link StateContext}
     * @param list    the {@link List} to format
     * @return the item list formatted as a string
     */
    public @NonNull String itemizeList(@NonNull StateContext context, @NonNull List<?> list) {
        ItemizeList action = new ItemizeList(this, context, list);
        RuntimeActionResult result = action.call();
        return (String) result.getResult();
    }

    /**
     * Posts the provided {@code attachments} in the given {@code channel}.
     *
     * @param context     the current {@link StateContext}
     * @param attachments the list of {@link Attachment} to post
     * @param channel     the Slack channel to post the attachment to
     * @param teamId      the identifier of the Slack workspace to post the attachment to
     */
    public void postAttachmentsMessage(@NonNull StateContext context, @NonNull List<Attachment> attachments,
                                       @NonNull String channel, @NonNull String teamId) {
        PostAttachmentsMessage action = new PostAttachmentsMessage(this, context, attachments, channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Builds and posts an attachment in the given {@code channel}.
     *
     * @param context     the current {@link StateContext}
     * @param pretext     the pre-text of the attachment
     * @param title       the title of the attachment
     * @param text        the text of the attachment
     * @param attachColor the color of the attachment
     * @param timestamp   the timestamp of the attachment
     * @param channel     the Slack channel to post the attachment to
     * @param teamId      the identifier of the Slack workspace to post the attachment to
     */
    public void postAttachmentsMessage(@NonNull StateContext context,
                                       String pretext,
                                       String title,
                                       @NonNull String text,
                                       String attachColor,
                                       String timestamp,
                                       @NonNull String channel,
                                       @NonNull String teamId) {
        PostAttachmentsMessage action = new PostAttachmentsMessage(this, context, pretext, title, text, attachColor,
                timestamp, channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Builds and posts an attachment in the given {@code channel}.
     *
     * @param context     the current {@link StateContext}
     * @param pretext     the pre-text of the attachment
     * @param title       the title of the attachment
     * @param text        the text of the attachment
     * @param attachColor the color of the attachment
     * @param channel     the Slack channel to post the attachment to
     * @param teamId      the identifier of the Slack workspace to post the attachment to
     */
    public void postAttachmentsMessage(StateContext context, String pretext, String title, String text,
                                       String attachColor, String channel, String teamId) {
        PostAttachmentsMessage action = new PostAttachmentsMessage(this, context, pretext, title, text, attachColor,
                channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code file} in the given {@code channel}.
     *
     * @param context the current {@link StateContext}
     * @param message the message to post with the file
     * @param file    the {@link File} to post
     * @param channel the Slack channel to post the file to
     * @param teamId  the identifier of the Slack workspace to post the file to
     */
    public void postFileMessage(StateContext context, String message, File file, String channel, String teamId) {
        PostFileMessage action = new PostFileMessage(this, context, message, file, channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code content} as a file in the given {@code channel}.
     *
     * @param context the current {@link StateContext}
     * @param title   the title of the file to post
     * @param message the message to post with the file
     * @param content the content of the file
     * @param channel the Slack channel to post the file to
     * @param teamId  the identifier of the Slack workspace to post the file to
     */
    public void postFileMessage(StateContext context, String title, String message, String content, String channel,
                                String teamId) {
        PostFileMessage action = new PostFileMessage(this, context, title, message, content, channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code layoutBlocks} to the given {@code channel}.
     *
     * @param context      the current {@link StateContext}
     * @param layoutBlocks the list of {@link LayoutBlock}s to post
     * @param channel      the Slack channel to post the file to
     * @param teamId       the identifier of the Slack workspace to post the layout blocks to
     */
    public void postLayoutBlocksMessage(StateContext context, List<LayoutBlock> layoutBlocks, String channel,
                                        String teamId) {
        PostLayoutBlocksMessage action = new PostLayoutBlocksMessage(this, context, layoutBlocks,
                channel, teamId);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code message} to the given {@code channel}.
     *
     * @param context the current {@link StateContext}
     * @param message the message to post
     * @param channel the Slack channel to post the message to
     * @param teamId  the identifier of the Slack workspace to post the message to
     * @return the timestamp of the posted message
     */
    public @NonNull String postMessage(@NonNull StateContext context, @NonNull String message, @NonNull String channel,
                                       @NonNull String teamId) {
        PostMessage action = new PostMessage(this, context, message, channel, teamId);
        RuntimeActionResult result = action.call();
        return (String) result.getResult();
    }

    /**
     * Posts the provided {@code message} with the provided {@code threadTs} in the given {@code channel}.
     * <p>
     * If the {@code threadTs} argument is equal to another message's {@code threadTs} the message is posted as a
     * thread reply.
     * <p>
     * Setting {@code threadTs} to {@code null} will post the message as a top-level message in the channel.
     *
     * @param context  the current {@link StateContext}
     * @param message  the message to post
     * @param channel  the Slack channel to post the message to
     * @param teamId   the identifier of the Slack workspace to post the message to
     * @param threadTs the timestamp of the parent thread message
     * @return the timestamp of the posted message
     */
    public @NonNull String postMessage(@NonNull StateContext context, @NonNull String message, @NonNull String channel,
                                       @NonNull String teamId, @Nullable String threadTs) {
        PostMessage action = new PostMessage(this, context, message, channel, teamId, threadTs);
        RuntimeActionResult result = action.call();
        return (String) result.getResult();
    }

    /**
     * Posts the provided {@code message} in the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context the current {@link StateContext}
     * @param message the message to post
     * @return the timestamp of the posted message
     */
    public @NonNull String reply(@NonNull StateContext context, @NonNull String message) {
        Reply action = new Reply(this, context, message);
        RuntimeActionResult result = action.call();
        return (String) result.getResult();
    }

    /**
     * Posts the provided {@code attachments} in the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context     the current {@link StateContext}
     * @param attachments the list of {@link Attachment}s to post
     */
    public void replyAttachmentsMessage(@NonNull StateContext context, @NonNull List<Attachment> attachments) {
        ReplyAttachmentsMessage action = new ReplyAttachmentsMessage(this, context, attachments);
        RuntimeActionResult result = action.call();
    }

    /**
     * Builds and posts the provided attachment to the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context     the current {@link StateContext}
     * @param pretext     the pre-text of the attachment
     * @param title       the title of the attachment
     * @param text        the text of the attachment
     * @param attachColor the color of the attachment
     * @param timestamp   the timestamp of the attachment
     */
    public void replyAttachmentsMessage(@NonNull StateContext context, String pretext, String title,
                                        @NonNull String text,
                                        String attachColor, String timestamp) {
        ReplyAttachmentsMessage action = new ReplyAttachmentsMessage(this, context, pretext, title, text, attachColor
                , timestamp);
        RuntimeActionResult result = action.call();
    }

    /**
     * Builds and posts the provided attachment to the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context     the current {@link StateContext}
     * @param pretext     the pre-text of the attachment
     * @param title       the title of the attachment
     * @param text        the text of the attachment
     * @param attachColor the color of the attachment
     */
    public void replyAttachmentsMessage(@NonNull StateContext context, String pretext, String title,
                                        @NonNull String text,
                                        String attachColor) {
        ReplyAttachmentsMessage action = new ReplyAttachmentsMessage(this, context, pretext, title, text, attachColor);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code file} in the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context the current {@link StateContext}
     * @param message the message to post
     * @param file    the file to post
     */
    public void replyFileMessage(@NonNull StateContext context, @NonNull String message, @NonNull File file) {
        ReplyFileMessage action = new ReplyFileMessage(this, context, message, file);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts a file with the provided {@code title}, {@code content} and {@code message} in the current channel.
     *
     * @param context the current {@link StateContext}
     * @param title   the title of the file to upload
     * @param message the message to associate to the uploaded file
     * @param content the content of the file to upload
     * @throws IllegalArgumentException if the provided {@code title}, {@code message}, or {@code content} is {@code
     *                                  empty}
     * @throws NullPointerException     if one of the provided parameter is {@code null}
     */
    public void replyFileMessage(@NonNull StateContext context, @NonNull String title, @NonNull String message,
                                 @NonNull String content) {
        ReplyFileMessage action = new ReplyFileMessage(this, context, title, message, content);
        RuntimeActionResult result = action.call();
    }

    /**
     * Posts the provided {@code layoutBlocks} in the current channel.
     * <p>
     * The current channel is extracted from the provided {@code context}.
     *
     * @param context      the current {@link StateContext}
     * @param layoutBlocks the list of {@link LayoutBlock}s to post
     */
    public void replyLayoutBlocksMessage(@NonNull StateContext context, @NonNull List<LayoutBlock> layoutBlocks) {
        ReplyLayoutBlocksMessage action = new ReplyLayoutBlocksMessage(this, context, layoutBlocks);
        RuntimeActionResult result = action.call();
    }

    /**
     * Registers the REST handler that manages OAuth requests sent by Slack when the app is installed.
     * <p>
     * The defined endpoint URI is {@code <basePath>/slack/oauth/redirect}, this URI must be specified in the
     * associated Slack app settings to ensure that Xatkit receives the OAuth requests and populate its internal data
     * structure with the new installation information.
     * <p>
     * <b>Note</b>: the OAuth REST handler is not started if the {@code configuration} used to initialize the
     * platform contains a Slack {@code token}. In this case the platform assumes that the app is currently under
     * development and does not authorize multiple installations.
     *
     * @see com.xatkit.core.server.XatkitServer
     */
    private void registerOAuthRestHandler() {
        this.xatkitBot.getXatkitServer().registerRestEndpoint(HttpMethod.GET, "/slack/oauth/redirect",
                RestHandlerFactory.createJsonRestHandler((headers, param, content) -> {
                    JsonObject result = new JsonObject();
                    String code = HttpUtils.getParameterValue("code", param);
                    try {
                        OAuthAccessResponse response = this.slack.methods().oauthAccess(OAuthAccessRequest.builder()
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .code(code)
                                .build());
                        logSlackApiResponse(response);
                        String teamId = response.getTeamId();
                        if (isNull(teamId)) {
                            result.addProperty("Error", "The Slack API response does not contain a team identifier");
                            return result;
                        }
                        String botAccessToken = response.getBot().getBotAccessToken();
                        if (isNull(botAccessToken)) {
                            result.addProperty("Error", "The Slack API response does not contain a bot access token");
                            return result;
                        }
                        Log.info("Adding installation mapping {0} -> {1}", teamId, botAccessToken);
                        this.teamIdToSlackToken.put(teamId, botAccessToken);
                        loadChannels(teamId);
                        this.notifyNewInstallation(teamId, botAccessToken);
                    } catch (IOException | SlackApiException e) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        PrintWriter printWriter = new PrintWriter(baos, true);
                        e.printStackTrace(printWriter);
                        result.addProperty("Error", baos.toString());
                        return result;
                    }
                    result.addProperty("Message", "Installed!");
                    return result;
                }));
    }

    /**
     * Notifies the started {@link com.xatkit.core.platform.io.RuntimeEventProvider}s that the Slack app has been
     * installed in a new workspace.
     *
     * @param teamId     the identifier of the workspace where the app has been installed
     * @param slackToken the Slack {@code token} associated to the new installation
     */
    private void notifyNewInstallation(String teamId, String slackToken) {
        this.getEventProviderMap().forEach((providerName, providerThread) -> {
            if (providerThread.getRuntimeEventProvider() instanceof SlackIntentProvider) {
                SlackIntentProvider slackIntentProvider =
                        (SlackIntentProvider) providerThread.getRuntimeEventProvider();
                slackIntentProvider.notifyNewInstallation(teamId, slackToken);
            }
        });
    }

    /**
     * Returns the {@link Map} containing the Slack {@code token}s associated to the identifiers of the workspaces
     * where the Slack app is installed.
     *
     * @return the {@link Map} containing the Slack {@code token}s associated to the identifiers of the workspaces
     * where the Slack app is installed
     */
    public Map<String, String> getTeamIdToSlackTokenMap() {
        return this.teamIdToSlackToken;
    }

    /**
     * Returns the Slack {@code token} associated to the provided {@code teamId}.
     *
     * @param teamId a workspace identifier
     * @return the Slack {@code token} associated to the provided {@code teamId} if it exists, {@code null} otherwise
     */
    public String getSlackToken(String teamId) {
        return teamIdToSlackToken.get(teamId);
    }

    /**
     * Returns the Slack API client.
     *
     * @return the Slack API client.
     */
    public Slack getSlack() {
        return slack;
    }

    /**
     * Returns the {@link StateContext} associated to the provided {@code teamId} and {@code channel}.
     * <p>
     * The provided {@code teamId} <b>must</b> be a valid workspace identifier, while the provided {@code channel}
     * can be an identifier or a channel name.
     *
     * @param teamId  the identifier of the workspace to create a session for
     * @param channel the workspace's {@code channel} to create a session for
     * @return the {@link StateContext} associated to the provided {@code teamId} and {@code channel}
     */
    public StateContext createSessionFromChannel(String teamId, String channel) {
        return this.xatkitBot.getOrCreateContext(teamId + "@" + this.getChannelId(teamId, channel));
    }

    /**
     * Retrieves the User ID associated to the provided {@code username} from the workspace identified with {@code
     * teamId}.
     * <p>
     * This method looks for any user with a {@code id}, {@code name}, or {@code realName} matching the provided {@code
     * username}, and returns its identifier.
     *
     * @param teamId   the idetnfier of the workspace containing the user to retrieve the ID of
     * @param username the name of the user to retrieve the ID of
     * @return the User ID if it exists
     * @throws XatkitException      if an error occurred when accessing the Slack API
     * @throws NullPointerException if the provided {@code teamId} or {@code username} is {@code null}
     */
    public String getUserId(String teamId, String username) {
        checkNotNull(teamId, "Cannot retrieve the user ID from the provided team %s", teamId);
        checkNotNull(username, "Cannot retrieve the user ID from the provided username %s", username);
        UsersListResponse usersListResponse;
        try {
            usersListResponse = this.getSlack().methods().usersList(UsersListRequest.builder()
                    .token(getSlackToken(teamId))
                    .build());
        } catch (IOException | SlackApiException e) {
            throw new XatkitException("An error occurred when accessing the Slack API, see attached exception", e);
        }
        logSlackApiResponse(usersListResponse);
        for (User user : usersListResponse.getMembers()) {
            if (Objects.equals(user.getId(), username) || Objects.equals(user.getName(), username) || Objects.equals(user.getRealName(), username)) {
                return user.getId();
            }
        }
        return null;
    }

    /**
     * Retrieves the ID of the {@code channelName} from the workspace identified with {@code teamId}.
     * <p>
     * This method supports channel IDs, names, as well as user names, real names, and display names (in this case
     * the private {@code im} channel ID between the bot and the user is returned). The returned ID can be used to
     * send messages to the channel.
     *
     * @param teamId      the identifier of the workspace containing the channel to retrieve the identifier
     * @param channelName the name of the channel to retrieve the ID from
     * @return the channel ID if it exists
     * @throws XatkitException if the provided {@code teamId} does not correspond to a valid Slack app installation,
     *                         or if the provided {@code channelName} does not correspond to any channel accessible
     *                         by the bot
     */
    public String getChannelId(String teamId, String channelName) {
        if (this.channelNames.containsKey(teamId)) {
            String id = this.channelNames.get(teamId).get(channelName);
            if (isNull(id)) {
                /*
                 * Check if the channel has been created since the previous lookup. This is not done by default because
                 * it reloads all the channel and may take some time.
                 */
                loadChannels(teamId);
                id = this.channelNames.get(teamId).get(channelName);
                if (isNull(id)) {
                    /*
                     * Cannot find the channel after a fresh lookup.
                     */
                    throw new XatkitException(MessageFormat.format("Cannot find the channel {0}, please ensure that " +
                            "the " +
                            "provided channel is either a valid channel ID, name, or a valid user name, real name, or" +
                            " " +
                            "display name", channelName));
                }
            }
            return id;
        } else {
            throw new XatkitException(MessageFormat.format("Unknown teamId {0}, please ensure that the bot is " +
                    "installed in this workspace", teamId));
        }
    }

    /**
     * Returns whether the {@code channelId} from the workspace {@code teamId} is a group channel (that can contain
     * multiple users) or not.
     *
     * @param teamId    the identifier of the workspace containing the channel to check
     * @param channelId the identifier of the Slack channel to check
     * @return {@code true} if the channel is a group channel, {@code false} otherwise
     * @throws XatkitException if the provided {@code teamId} does not correspond to a valid Slack app installation
     */
    public boolean isGroupChannel(String teamId, String channelId) {
        if (this.userChannels.containsKey(teamId)) {
            /*
             * First check if it's a user channel, if so no need to do additional calls on the Slack API, we know it's
             * not a group channel.
             */
            if (this.userChannels.get(teamId).contains(channelId)) {
                return false;
            } else {
                if (this.groupChannels.get(teamId).contains(channelId)) {
                    return true;
                } else {
                    /*
                     * Reload the channels in case the group channel has been created since the last check.
                     */
                    loadChannels(teamId);
                    return this.groupChannels.get(teamId).contains(channelId);
                }
            }
        } else {
            throw new XatkitException(MessageFormat.format("Unknown team {0}, please ensure that the bot is installed" +
                    " in this workspace", teamId));
        }
    }

    /**
     * Loads the channels associated to the workspace's {@code teamId} and store channel-related information.
     * <p>
     * The stored information can be retrieved with dedicated methods, and reduce the number of calls to the Slack API.
     *
     * @see #getChannelId(String, String)
     * @see #isGroupChannel(String, String)
     */
    private void loadChannels(String teamId) {
        Map<String, String> workspaceChannelNames = new HashMap<>();
        List<String> workspaceGroupChannels = new ArrayList<>();
        List<String> workspaceUserChannels = new ArrayList<>();
        this.channelNames.put(teamId, workspaceChannelNames);
        this.groupChannels.put(teamId, workspaceGroupChannels);
        this.userChannels.put(teamId, workspaceUserChannels);
        String teamSlackToken = teamIdToSlackToken.get(teamId);
        if (isNull(teamSlackToken)) {
            throw new XatkitException(MessageFormat.format("Cannot load the channels for team {0}, the bot is not " +
                    "installed in this workspace", teamId));
        }
        try {
            ConversationsListResponse response =
                    slack.methods().conversationsList(ConversationsListRequest.builder()
                            .token(teamSlackToken)
                            .types(Arrays.asList(ConversationType.PUBLIC_CHANNEL, ConversationType.PUBLIC_CHANNEL,
                                    ConversationType.IM, ConversationType.MPIM))
                            .build());
            logSlackApiResponse(response);
            for (Conversation conversation : response.getChannels()) {
                String conversationId = conversation.getId();
                /*
                 * Store the conversation ID as an entry for itself, this is because we cannot differentiate IDs from
                 * regular strings when retrieving a channel ID.
                 */
                workspaceChannelNames.put(conversationId, conversationId);
                if (nonNull(conversation.getName())) {
                    workspaceChannelNames.put(conversation.getName(), conversation.getId());
                    workspaceGroupChannels.add(conversation.getId());
                    Log.debug("Conversation name: {0}, ID: {1}", conversation.getName(), conversationId);
                } else {
                    String userId = conversation.getUser();
                    UsersInfoResponse userResponse = slack.methods().usersInfo(UsersInfoRequest.builder()
                            .token(teamSlackToken)
                            .user(userId)
                            .build());
                    logSlackApiResponse(userResponse);
                    workspaceChannelNames.put(userResponse.getUser().getName(), conversationId);
                    workspaceChannelNames.put(userResponse.getUser().getRealName(), conversationId);
                    workspaceChannelNames.put(userResponse.getUser().getProfile().getDisplayName(), conversationId);
                    workspaceUserChannels.add(conversationId);
                    Log.debug("User name: {0}", userResponse.getUser().getName());
                    Log.debug("User real name: {0}", userResponse.getUser().getRealName());
                    Log.debug("User display name: {0}", userResponse.getUser().getProfile().getDisplayName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
