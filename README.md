Xatkit Slack Platform
=====

[![License Badge](https://img.shields.io/badge/license-EPL%202.0-brightgreen.svg)](https://opensource.org/licenses/EPL-2.0)
[![Wiki Badge](https://img.shields.io/badge/doc-wiki-blue)](https://github.com/xatkit-bot-platform/xatkit/wiki/Xatkit-Slack-Platform)


Receive and send messages from [Slack](https://slack.com).

The Slack platform is a concrete implementation of the [*ChatPlatform*](https://github.com/xatkit-bot-platform/xatkit-chat-platform).

## Providers

The Slack platform defines the following providers:

| Provider                   | Type  | Context Parameters | Description                                                  |
| -------------------------- | ----- | ------------------ | ------------------------------------------------------------ |
| ChatProvider | Intent | - `chat.channel`: the identifier of the channel that sent the message<br/> - `chat.username`: the name of the user that sent the message<br/> - `chat.rawMessage`: the raw message sent by the user (before NLP processing) | Receive messages from a communication channel and translate them into Xatkit-compatible intents (*inherited from [ChatPlatform](https://github.com/xatkit-bot-platform/xatkit-chat-platform)*) |
| SlackIntentProvider | Intent | - `slack.team`: the identifier of the Slack workspace containing the channel that sent the message <br/> - `slack.channel`: the identifier of the Slack channel that sent the message<br/> - `slack.username`: the name of the Slack user that sent the message<br/> - `slack.rawMessage`: the raw message sent by the user (before NLP processing)<br/> - `userId`: the Slack unique identifier of the user that sent the message<br/> - `userEmail`: the email address of the Slack user that sent the message<br/> - `slack.threadTs`: the timestamp of the thread of the received message (*empty* if the message wasn't post in a thread)<br/> - `slack.messageTs`: the timestamp of the received message | Receive messages from Slack and translates them into Xatkit-compatible intents. Note that `slack.channel`, `slack.username`, and `slack.rawMessage` contain the same values as `chat.channel`, `chat.username`, and `chat.rawMessage` |

## Actions

| Action | Parameters                                                   | Return                         | Return Type | Description                                                 |
| ------ | ------------------------------------------------------------ | ------------------------------ | ----------- | ----------------------------------------------------------- |
| PostMessage | - `message`(**String**): the message to post<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name)<br />- (*Optional*) `threadTs` (**String**): the timestamp of the thread to post the message in | The posted message | String (the timestamp of the posted message) | Posts the provided `message` to the given Slack `channel` (*inherited from [ChatPlatform](https://github.com/xatkit-bot-platform/xatkit-chat-platform)*). |
| PostMessage | - `message`(**String**): the message to post<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name)<br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to <br/>- (*Optional*) `threadTs` (**String**): the timestamp of the thread to post the message in | The posted message | String (the timestamp of the posted message) | Posts the provided `message` to the Slack `channel` contained in the `teamId` workspace |
| Reply | - `message` (**String**): the message to post as a reply | The posted message | String (the timestamp of the posted message) | Posts the provided `message` as a reply to a received message (*inherited from [ChatPlatform](https://github.com/xatkit-bot-platform/xatkit-chat-platform)*). If the received message is contained in a thread the reply is appended to the same thread. |
| PostFileMessage | - `message` (**String**): the message to post with the file<br/> - `file` ([**File**](https://docs.oracle.com/javase/7/docs/api/java/io/File.html)): the file to post<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name) <br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to | `null` | `null` | Posts the provided `file` with the provided `message` to the Slack `channel` contained in the `teamId` workspace (the file title is automatically set with the name of the provided `file`) |
| PostFileMessage | - `title` (**String**): the associated to the file to post<br/> - `message` (**String**): the message to post with the file<br/> - `content` (**String**): the raw content of the file to post<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name) <br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to| `null` | `null` | Posts a file with the provided `content` and `title` to the Slack `channel` contained in the `teamId` workspace |
| ReplyFileMessage | - `message` (**String**): the message to post with the file<br/> - `file` ([**File**](https://docs.oracle.com/javase/7/docs/api/java/io/File.html)): the file to post<br/> | `null` | `null` | Posts the provided `file` as a reply to a received message |
| PostAttachmentsMessage | - `attachments` ([**List\<Attachment\>**](https://github.com/seratch/jslack): the attachments to set in the message<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name) <br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to | `null` | `null` | Posts a message with the given `attachments` to the Slack `channel` contained in the `teamId` workspace |
| PostAttachmentsMessage | - `pretext` (**String**): the text to display before the attachment<br/> - `title` (**String**): the title of the attachment<br/> - `text` (**String**): the text of the attachment<br/> - `attchColor` (**String**): the color of the attachment (in HEX format)<br/> - `timestamp` (**String**): the timestamp associated to the attachment<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name) <br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to | `null` | `null` | Posts a message containing the given `pretext` with an attachment containing the provided `title`, `text`, `attchColor`, and `timestamp` to the Slack `channel` contained in the `teamId` workspace |
| PostAttachmentMessage | - `pretext` (**String**): the text to display before the attachment<br/> - `title` (**String**): the title of the attachment<br/> - `text` (**String**): the text of the attachment<br/> - `attchColor` (**String**): the color of the attachment (in HEX format)<br/> - `channel` (**String**): the name or raw identfier of the Slack  channel to post the message to (direct messages can be sent using the target username as channel name) <br /> - `teamId` (**String**): the identifier of the Slack workspace containing the channel to post to | `null` | `null` | Posts a message containing the given `pretext` with an attachment containing the provided `title`, `text`, and `attchColor` to the Slack `channel` contained in the `teamId` workspace (the attachment `timestamp` is automatically set to the current date) |
| ReplyAttachmentMessage | - `attachments` ([**List\<Attachment\>**](https://github.com/seratch/jslack): the attachments to set in the message | `null` | `null` | Posts a message with the given `attachments` as a reply to a received message |
| ReplyAttachmentMessage | - `pretext` (**String**): the text to display before the attachment<br/> - `title` (**String**): the title of the attachment<br/> - `text` (**String**): the text of the attachment<br/> - `attchColor` (**String**): the color of the attachment (in HEX format)<br/> - `timestamp` (**String**): the timestamp associated to the attachment<br/> | `null` | `null` | Posts a message containing the given `pretext` with an attachment containing the provided `title`, `text`, `attchColor`, and `timestamp` as a reply to a received message |
| ReplyAttachmentsMessage | `pretext` (**String**): the text to display before the attachment<br/> - `title` (**String**): the title of the attachment<br/> - `text` (**String**): the text of the attachment<br/> - `attchColor` (**String**): the color of the attachment (in HEX format) | `null` | `null` | Posts a message containing the given `pretext` with an attachment containing the provided `title`, `text`, and `attchColor` as a reply to a received message (the attachment `timestamp` is automatically set to the current date) |
| IsOnline | `username` (**String**): the name of the user to check (can be its ID, name, or real name)<br/> - `teamId` (**String**): the identifier of the Slack workspace containing the user to check | Whether the provided `username` is online. | `Boolean` | Returns whether the provided `username` is online in the given `teamId`. <br/>**Note**: the provided `username` can be the user's ID, name, or real name. |
| ItemizeList | - `list` ([**List**](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)): the list to itemize | A String presenting the provided `list` as a set of items | String | Creates a set of items from the provided `list`. This actions relies on `Object.toString()` to print each item's content |
| ItemizeList | - `list` ([**List**](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)): the list to itemize<br/> - `formatter` ([**Formatter**](https://xatkit-bot-platform.github.io/xatkit-runtime-docs/releases/snapshot/doc/com/xatkit/core/platform/Formatter.html) the formatter used to print each item | A String presenting the provided `list` as a set of items formatted with the given `formatter` | String | Creates a set of items from the provided `list`. This action relies on the provided `formatter` to print each item's content |
| EnumerateList | - `list` ([**List**](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)): the list to enumerate | A String presenting the provided `list` as an enumeration | String | Creates an enumeration from the provided `list`. This actions relies on `Object.toString()` to print each item's content |
| EnumerateList | - `list` ([**List**](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)): the list to enumerate<br/> - `formatter` ([**Formatter**](https://xatkit-bot-platform.github.io/xatkit-runtime-docs/releases/snapshot/doc/com/xatkit/core/platform/Formatter.html) the formatter used to print each item | A String presenting the provided `list` as an enumeration formatted with the given `formatter` | String | Creates an enumeration from the provided `list`. This action relies on the provided `formatter` to print each item's content |

## Options

The Slack platform supports the following configuration options

| Key                  | Values | Description                                                  | Constraint    |
| -------------------- | ------ | ------------------------------------------------------------ | ------------- |
| `xatkit.slack.token` | String | The [Slack token](https://api.slack.com/) used by Xatkit to deploy the bot | **Optional** (not needed if `xatkit.slack.client.id` and `xatkit.slack.client.secret` are specified, mandatory otherwise) |
| `xatkit.slack.client.id` | String | The Slack app's client identifier used by Xatkit to start the Slack platform and allow new installations of the app | **Optional** (not needed when starting the Slack platform in *development mode* with a valid `xatkit.slack.token`) |
| `xatkit.slack.ignore_fallback_on_group_channels` | Boolean | Specifies whether fallback intents should be ignored in group channels | **Optional** (default `false`) |
| `xatkit.slack.listen_mentions_on_group_channels` | Boolean | Specifies whether the bot should only listen to mentions in group channels | **Optional** (default `false`) |

## Installing and using the Slack platform

An example of a bot that uses Slack and the GitHub platforms is available in our [repository of examples](https://github.com/xatkit-bot-platform/xatkit-examples/tree/master/GitHubBots/GithubBot).

Make sure also to include this dependency to your pom

```xml
    <dependency>
        <groupId>com.xatkit</groupId>
        <artifactId>slack-platform</artifactId>
        <version>3.0.1-SNAPSHOT</version>
    </dependency>
```
