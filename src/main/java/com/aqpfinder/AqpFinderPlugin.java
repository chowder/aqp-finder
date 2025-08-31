package com.aqpfinder;

import com.aqpfinder.config.RecommendationMode;
import com.google.inject.Provides;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Aqp Finder"
)
public class AqpFinderPlugin extends Plugin implements KeyListener {
    @Inject
    private Client client;

    @Inject
    private AqpFinderConfig config;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private Notifier notifier;

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AqpFinderOverlay overlay;

    // QP pattern constants
    private static final String QP_PATTERN = "q p";
    private static final String MICRO_QP_PATTERN = "qp";

    // Pixel offset constants
    private static final int Q_VERTICAL_ALIGNMENT_OFFSET = 4;
    private static final int CHAT_ICON_WIDTH = 13;
    private static final int PRIVATE_MESSAGE_OFFSET = -15;
    private static final int SEGMENT_Q_ALIGNMENT_OFFSET = 8;
    private static final int FRIENDS_CHAT_RANK_ICON_WIDTH = 11;
    private static final int CHARACTER_PADDING = 2;
    private static final int MIN_SPACES_DISTANCE = 3;
    
    // Allowed message types for QP processing
    private static final Set<ChatMessageType> ALLOWED_MESSAGE_TYPES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.FRIENDSCHAT
        ))
    );

    private boolean isAltPressed = false;
    private String altBuffer = "";

    /**
     * An immutable map of characters to size in pixels of that character in OSRS chatbox.
     */
    private final Map<Character, Integer> characterSizeMap = createCharacterSizeMap();

    /**
     * Creates an immutable map of characters to size in pixels of that character in OSRS chatbox.
     * Sizes listed cover the visible character only. Each character is padded by 2 blank pixels when printed in chat.
     *
     * @return immutable map of (character, size) pairs.
     */
    private static Map<Character, Integer> createCharacterSizeMap() {
        Map<Character, Integer> result = new HashMap<>();
        // Upper case
        result.put('A', 6);
        result.put('B', 5);
        result.put('C', 5);
        result.put('D', 5);
        result.put('E', 4);
        result.put('F', 4);
        result.put('G', 6);
        result.put('H', 5);
        result.put('I', 1);
        result.put('J', 5);
        result.put('K', 5);
        result.put('L', 4);
        result.put('M', 7);
        result.put('N', 6);
        result.put('O', 6);
        result.put('P', 5);
        result.put('Q', 6);
        result.put('R', 5);
        result.put('S', 5);
        result.put('T', 3);
        result.put('U', 6);
        result.put('V', 5);
        result.put('W', 7);
        result.put('X', 5);
        result.put('Y', 5);
        result.put('Z', 5);

        // Lower case
        result.put('a', 5);
        result.put('b', 5);
        result.put('c', 4);
        result.put('d', 5);
        result.put('e', 5);
        result.put('f', 4);
        result.put('g', 5);
        result.put('h', 5);
        result.put('i', 1);
        result.put('j', 4);
        result.put('k', 4);
        result.put('l', 1);
        result.put('m', 7);
        result.put('n', 5);
        result.put('o', 5);
        result.put('p', 5);
        result.put('q', 5);
        result.put('r', 3);
        result.put('s', 5);
        result.put('t', 3);
        result.put('u', 5);
        result.put('v', 5);
        result.put('w', 5);
        result.put('x', 5);
        result.put('y', 5);
        result.put('z', 5);

        // Numbers
        result.put('0', 6);
        result.put('1', 4);
        result.put('2', 6);
        result.put('3', 5);
        result.put('4', 5);
        result.put('5', 5);
        result.put('6', 6);
        result.put('7', 5);
        result.put('8', 6);
        result.put('9', 6);

        // Symbols
        result.put(' ', 1);
        result.put(':', 1);
        result.put(';', 2);
        result.put('"', 3);
        result.put('@', 11);
        result.put('!', 1);
        result.put('.', 1);
        result.put('|', 1);
        result.put('\'', 2);
        result.put(',', 2);
        result.put('(', 2);
        result.put(')', 2);
        result.put('+', 5);
        result.put('-', 4);
        result.put('<', 4);
        result.put('>', 4);
        result.put('=', 6);
        result.put('?', 6);
        result.put('*', 7);
        result.put('/', 4);
        result.put('$', 6);
        result.put('£', 8);
        result.put('^', 6);
        result.put('{', 3);
        result.put('}', 3);
        result.put('[', 3);
        result.put(']', 3);
        result.put('&', 9);
        result.put('#', 11);
        result.put('°', 4);

        // Other
        result.put('\u00A0', 1); // no-break space

        return Collections.unmodifiableMap(result);
    }

    /**
     * An immutable list of characters that end a sentence in player chat.
     */
    private final List<Character> endSentenceCharList = createEndSentenceCharList();

    /**
     * Creates an immutable list of characters that end a sentence in player chat.
     * A character is considered to end a sentence if an immediately following letter is formatted to upper case in chat.
     *
     * @return immutable list of end of sentence characters.
     */
    private static List<Character> createEndSentenceCharList() {
        List<Character> result = new ArrayList<>();

        result.add('.');
        result.add('!');
        result.add('?');

        return Collections.unmodifiableList(result);
    }

    @Getter
    private boolean lastMessageIncludesQP = false;
    private boolean lastQPFromPM = false;
    private ChatMessageType lastQPMessageType = null;
    private Integer[] lastMessageSegmentIndex = new Integer[0];
    private String[] lastMessageQPIndex = new String[0];
    private String chatBoxTypedText = "";
    private int chatBoxTypedTextLength = 0;
    @Getter
    private final List<String> overlayText = new ArrayList<>();
    @Getter
    private final List<Integer> overlayTextColour = new ArrayList<>();

    /**
     * Data class to hold parsed QP segments and their corresponding QP patterns
     */
    private static class QPSegments {
        public final List<String> messageSegments;
        public final List<String> qpPatterns;

        public QPSegments(List<String> messageSegments, List<String> qpPatterns) {
            this.messageSegments = messageSegments;
            this.qpPatterns = qpPatterns;
        }
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(this);
        if (config.showOverlay()) {
            overlayManager.add(overlay);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(this);
        if (config.showOverlay()) {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getKey().equals("showOverlay")) {
            if (config.showOverlay()) {
                overlayManager.add(overlay);
                if (lastMessageIncludesQP) {
                    refreshChatBoxTypedText();
                    updateOverlayText();
                }
            } else {
                overlayManager.remove(overlay);
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        MessageNode messageNode = chatMessage.getMessageNode();
        String message = messageNode.getValue();
        boolean update = false;

        // Only process allowed message types
        if (!ALLOWED_MESSAGE_TYPES.contains(messageNode.getType())) {
            return;
        }

        // Clear previous QP state only if processing same message type
        if (lastMessageIncludesQP && messageNode.getType().equals(lastQPMessageType)) {
            lastMessageIncludesQP = false;
            lastMessageSegmentIndex = null;
        }

        if (containsQP(message)) {
            lastMessageIncludesQP = true;
            lastQPMessageType = messageNode.getType();
            String originalMessage = message;

            // Preprocess message to handle HTML entities
            String processedMessage = preprocessMessage(message);
            log.debug("Original message: '{}', Processed message: '{}'", message, processedMessage);

            // Parse QP segments from the processed message
            QPSegments qpSegments = parseQPSegments(processedMessage);
            log.debug("Parsed QP segments: {} with patterns: {}", qpSegments.messageSegments, qpSegments.qpPatterns);

            // Calculate segment lengths with offsets
            List<Integer> segmentLengths = calculateSegmentLengths(
                    qpSegments.messageSegments, qpSegments.qpPatterns, messageNode);

            // Calculate cumulative segment indices
            Integer[] segmentIndex = calculateSegmentIndices(segmentLengths, qpSegments.qpPatterns);

            // Save arrays for dynamic overlay
            lastMessageSegmentIndex = Arrays.copyOf(segmentIndex, segmentIndex.length);
            lastMessageQPIndex = qpSegments.qpPatterns.toArray(new String[0]);

            // Adjust segment lengths for display
            adjustSegmentLengthsForDisplay(segmentLengths, segmentIndex);

            // Generate recommendations
            if (config.recommendationMode().equals(RecommendationMode.SPACES)) {
                message = originalMessage + "   " + segmentLengths.stream().map(this::spacesToW).collect(Collectors.toList());
            } else {
                message = originalMessage + "   " + segmentLengths;
            }

            if (config.giveInlineHints()) {
                messageNode.setValue(message);
            }
            update = true;
        }

        if (lastMessageIncludesQP && config.showOverlay()) {
            refreshChatBoxTypedText();
            updateOverlayText();
        }

        if (update) {
            messageNode.setRuneLiteFormatMessage(messageNode.getValue());

            if (config.notifyOnQP()) {
                notifier.notify("A q\u00A0p opportunity!");
            }
        }
    }

    /**
     * Preprocesses message text to convert HTML-like entities back to regular characters.
     * 
     * @param message the message to preprocess
     * @return message with HTML entities converted to regular characters
     */
    private String preprocessMessage(String message) {
        if (message == null) {
            return null;
        }
        
        return message
            .replace("<lt>", "<")
            .replace("<gt>", ">");
    }
    
    /**
     * Returns whether the provided string contains at least one of the config enabled qp strings.
     *
     * @param message the string to check for qp
     * @return true if the specified message contains at least one qp
     */
    private boolean containsQP(String message) {
        String processedMessage = preprocessMessage(message);
        return processedMessage.contains(QP_PATTERN) || (config.findMicroQPs() && processedMessage.contains(MICRO_QP_PATTERN));
    }

    /**
     * Parses a message containing QP patterns and returns segments and QP types.
     *
     * @param message the message to parse
     * @return QPSegments containing message segments and corresponding QP patterns
     */
    private QPSegments parseQPSegments(String message) {
        List<String> messageSegments = new ArrayList<>();
        List<String> qpPatterns = new ArrayList<>();

        // Ensure message is preprocessed
        String remainingMessage = preprocessMessage(message);
        while (containsQP(remainingMessage)) {
            int nextQPIndex = remainingMessage.indexOf(QP_PATTERN);
            int nextMicroQPIndex = config.findMicroQPs() ? remainingMessage.indexOf(MICRO_QP_PATTERN) : -1;

            String nextQP;
            int nextQPLocation;

            // Determine which QP pattern comes first
            if (nextQPIndex >= 0 && (nextMicroQPIndex == -1 || nextQPIndex < nextMicroQPIndex)) {
                nextQP = QP_PATTERN;
                nextQPLocation = nextQPIndex;
            } else {
                nextQP = MICRO_QP_PATTERN;
                nextQPLocation = nextMicroQPIndex;
            }

            messageSegments.add(remainingMessage.substring(0, nextQPLocation));
            qpPatterns.add(nextQP);

            remainingMessage = remainingMessage.substring(nextQPLocation + nextQP.length());
        }

        return new QPSegments(messageSegments, qpPatterns);
    }

    /**
     * Returns the pixel width of the friends chat rank icon of the specified player.
     *
     * @param name the name of the player to check
     * @return integer width of rank icon
     */
    private int getFriendsChatRankIconSize(String name) {
        String cleanName = name.replaceAll("<img=\\d+>", "");
        FriendsChatMember friendsChatMember = client.getFriendsChatManager().findByName(cleanName);
        
        if (friendsChatMember == null) {
            log.warn("Could not find friends chat member: \"{}\"", cleanName);
            return 0;
        }
        
        FriendsChatRank rank = friendsChatMember.getRank();
        return rank.equals(FriendsChatRank.UNRANKED) ? 0 : FRIENDS_CHAT_RANK_ICON_WIDTH;
    }

    /**
     * Returns the pixel width of the specified string in the chatbox.
     *
     * @param chatMsg the String to measure
     * @return the integer width of the specified String
     */
    private int getChatLength(String chatMsg) {
        if (chatMsg == null) {
            return -5;
        }

        try {
            return chatMsg.chars()
                    .mapToObj(ch -> (char) ch)
                    .map(key -> characterSizeMap.get(key) + CHARACTER_PADDING)
                    .reduce(0, (a, b) -> a + b);
        } catch (NullPointerException e) {
            return -5;
        }
    }

    /**
     * Returns the pixel width of a player name including chat icon if relevant.
     *
     * @param name the player name to measure
     * @return the integer width of the specified name with chat icon
     */
    private int getNameLength(String name) {
        if (name == null) {
            return -5;
        }

        return getChatLength(name.replaceAll("<img=\\d+>", "@"));
    }

    /**
     * Calculates segment lengths with appropriate offsets based on message type and sender.
     *
     * @param segments    the parsed message segments
     * @param qpPatterns  the QP patterns found
     * @param messageNode the message node containing metadata
     * @return list of calculated segment lengths in pixels
     */
    private List<Integer> calculateSegmentLengths(List<String> segments, List<String> qpPatterns, MessageNode messageNode) {
        List<Integer> segmentLengths = segments.stream()
                .map(this::getChatLength)
                .collect(Collectors.toList());
        
        log.debug("Initial segment lengths: {} for segments: {}", segmentLengths, segments);

        // Apply message type specific offsets
        if (messageNode.getType().equals(ChatMessageType.PRIVATECHAT)) {
            // Private message from another player
            int oldLength = segmentLengths.get(0);
            segmentLengths.set(0, segmentLengths.get(0) + Q_VERTICAL_ALIGNMENT_OFFSET + PRIVATE_MESSAGE_OFFSET);
            log.debug("Private message offset: {} -> {} (added {})", oldLength, segmentLengths.get(0), Q_VERTICAL_ALIGNMENT_OFFSET + PRIVATE_MESSAGE_OFFSET);
            lastQPFromPM = true;
        } else if (messageNode.getType().equals(ChatMessageType.PRIVATECHATOUT)) {
            // Private message sent by local player
            int oldLength = segmentLengths.get(0);
            segmentLengths.set(0, segmentLengths.get(0) + Q_VERTICAL_ALIGNMENT_OFFSET);
            log.debug("Private message out offset: {} -> {} (added {})", oldLength, segmentLengths.get(0), Q_VERTICAL_ALIGNMENT_OFFSET);
            lastQPFromPM = true;
        } else {
            // Public message - calculate name length differences
            String sender = messageNode.getName();
            String localPlayer = client.getLocalPlayer().getName();

            int senderNameLength = getNameLength(sender);
            int localNameLength = getNameLength(localPlayer);
            log.debug("Name lengths - sender: {} ({}), local: {} ({})", sender, senderNameLength, localPlayer, localNameLength);

            // Account for Friends Chat icons
            if (messageNode.getType().equals(ChatMessageType.FRIENDSCHAT)) {
                int senderIconSize = getFriendsChatRankIconSize(messageNode.getName());
                int localIconSize = getFriendsChatRankIconSize(client.getLocalPlayer().getName());
                senderNameLength += senderIconSize;
                localNameLength += localIconSize;
                log.debug("Friends chat icon sizes - sender: {}, local: {}", senderIconSize, localIconSize);
            }

            int oldLength = segmentLengths.get(0);
            int nameOffset = senderNameLength - localNameLength;
            segmentLengths.set(0, segmentLengths.get(0) + Q_VERTICAL_ALIGNMENT_OFFSET + nameOffset);
            log.debug("Public message offset: {} -> {} (added {} + {} name offset)", oldLength, segmentLengths.get(0), Q_VERTICAL_ALIGNMENT_OFFSET, nameOffset);
            lastQPFromPM = false;
        }

        // Account for local player chat icon
        boolean hasIcon = (client.getVarbitValue(Varbits.ACCOUNT_TYPE) != 0 || config.hasIcon());
        boolean isPrivateOut = messageNode.getType().equals(ChatMessageType.PRIVATECHATOUT);
        if (hasIcon && !isPrivateOut) {
            int oldLength = segmentLengths.get(0);
            segmentLengths.set(0, segmentLengths.get(0) - CHAT_ICON_WIDTH);
            log.debug("Chat icon offset: {} -> {} (subtracted {})", oldLength, segmentLengths.get(0), CHAT_ICON_WIDTH);
        }
        
        log.debug("Final segment lengths: {}", segmentLengths);
        return segmentLengths;
    }

    /**
     * Calculates cumulative segment indices for QP positioning.
     *
     * @param segmentLengths the calculated segment lengths
     * @param qpPatterns     the QP patterns found
     * @return array of cumulative segment indices
     */
    private Integer[] calculateSegmentIndices(List<Integer> segmentLengths, List<String> qpPatterns) {
        Integer[] segmentIndex = new Integer[segmentLengths.size()];
        int total = segmentLengths.get(0);
        segmentIndex[0] = total;

        for (int i = 1; i < segmentIndex.length; i++) {
            total += segmentLengths.get(i) + getChatLength(qpPatterns.get(i - 1));
            segmentIndex[i] = total;
        }

        return segmentIndex;
    }

    /**
     * Adjusts segment lengths for display based on configuration settings.
     *
     * @param segmentLengths the calculated segment lengths (modified in place)
     * @param segmentIndex   the cumulative segment indices
     */
    private void adjustSegmentLengthsForDisplay(List<Integer> segmentLengths, Integer[] segmentIndex) {
        // Align segments with vertical of the q
        for (int i = 1; i < segmentLengths.size(); i++) {
            segmentLengths.set(i, segmentLengths.get(i) + SEGMENT_Q_ALIGNMENT_OFFSET);
        }

        if (config.showCumulative()) {
            // Replace with cumulative values
            for (int i = 0; i < segmentLengths.size(); i++) {
                segmentLengths.set(i, segmentIndex[i]);
            }
        } else {
            // Mark impossible segments and adjust first possible
            for (int i = 0; i < segmentLengths.size() - 1; i++) {
                if (segmentIndex[i] < 0) {
                    segmentLengths.set(i + 1, segmentIndex[i + 1]);
                    segmentLengths.set(i, -1);
                }
            }
            if (segmentIndex[segmentIndex.length - 1] < 0) {
                segmentLengths.set(segmentIndex.length - 1, -1);
            }
        }
    }

    /**
     * Strips chat command prefixes from the input text.
     *
     * @param text the raw chat input text
     * @return text with command prefix removed
     */
    private String stripChatCommandPrefix(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Check prefixes from most specific to least specific
        String[] prefixes = {
                "/gc ", "/GC ", "/Gc ", "/gC ",  // Guild/Guest clan chat
                "//",                            // Clan chat
                "/p ", "/P ",                    // Private message
                "/f ", "/F ",                    // Friends chat
                "/c ", "/C ",                    // Clan chat
                "/g ", "/G ",                    // Probably guild chat
                "/"                              // Generic command
        };

        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length());
            }
        }

        return text;
    }

    /**
     * Returns a string recommendation including the number of space characters to give a total width in pixels equal to the given integer.
     * <p>
     * If the given integer width cannot be met with only spaces then another character is included.
     * This character cannot be a letter as case is not guaranteed.
     *
     * @param pixels the integer pixel width to match
     * @return a String that contains the number of spaces (and other characters) required to match the specified integer width, or "impossible" if pixels is too small
     */
    private String spacesToW(int pixels) {
        String recommendation = "";

        if (pixels % 3 == 1) {
            pixels -= 4;
            recommendation += ", and ";
        }

        if (pixels % 3 == 2) {
            pixels -= 5;
            recommendation += "\" and ";
        }

        if (pixels < 0) {
            recommendation = "impossible";
        } else {
            recommendation += pixels / 3 + " spaces";
        }

        return recommendation;
    }

    @Provides
    AqpFinderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AqpFinderConfig.class);
    }

    /**
     * Formats the given String to match format of public chat messages.
     * <p>
     * Format rules:
     * <ul>
     *  <li>If the first non-whitespace character of a sentence is a letter then it is forced upper case.</li>
     *  <li>Any letter immediately proceeded by a letter is forced lower case.</li>
     *  <li>A sentence is ended by the characters: . ! ?</li>
     * </ul>
     *
     * @param chatText the string to be formatted as a public chat message
     * @return the formatted string or an empty string if chatText is null
     */
    private String formatChatText(String chatText) {
        if (chatText == null) {
            return "";
        }

        char[] chatTextArray = chatText.toCharArray();
        boolean inWord = false;
        boolean newSentence = true;

        for (int i = 0; i < chatTextArray.length; i++) {
            char ch = chatTextArray[i];

            if (Character.isLetter(ch)) {
                if (inWord) {
                    chatTextArray[i] = Character.toLowerCase(ch);
                }
                if (newSentence) {
                    chatTextArray[i] = Character.toUpperCase(ch);
                }
                inWord = true;
            } else {
                inWord = false;
            }

            if (endSentenceCharList.contains(ch)) {
                newSentence = true;
            } else if (!Character.isWhitespace(ch)) {
                newSentence = false;
            }
        }

        return new String(chatTextArray);
    }

    /**
     * Updates the stored string to be the current string typed into the input of the last "q p" message received.
     */
    private void refreshChatBoxTypedText() {
        String newText = "";
        if (lastQPFromPM) {
            newText = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        } else {
            newText = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
        }

        newText = stripChatCommandPrefix(newText);

        newText = formatChatText(newText);

        if (!Objects.equals(newText, chatBoxTypedText)) {
            chatBoxTypedText = newText;
            chatBoxTypedTextLength = getChatLength(chatBoxTypedText);
        }

        if (!isAltPressed && altBuffer.equals("0176")) {
            chatBoxTypedTextLength += 6;
            altBuffer = "";
        }
    }

    /**
     * Creates recommendation lines and corresponding red colour value for all "q p"s in the most recent message containing at least one.
     * Recommendations take the current chat input into account.
     */
    private void updateOverlayText() {
        overlayText.clear();
        overlayTextColour.clear();
        for (int i = 0; i < lastMessageSegmentIndex.length; i++) {
            Integer seg = lastMessageSegmentIndex[i];
            String qp = lastMessageQPIndex[i];

            int scaledPercentPixelsToW;
            if (seg < 0) {
                scaledPercentPixelsToW = 255;
            } else {
                scaledPercentPixelsToW = Math.min(Math.round(Math.abs(((seg - chatBoxTypedTextLength) * 255) / (float) seg)), 255);
            }

            String lineText = "error";

            if (seg - chatBoxTypedTextLength >= MIN_SPACES_DISTANCE) {
                lineText = spacesToW(seg - chatBoxTypedTextLength);
            } else if (MIN_SPACES_DISTANCE > seg - chatBoxTypedTextLength && seg - chatBoxTypedTextLength > 0) {
                lineText = "Too close.";
            } else if (seg == chatBoxTypedTextLength) {

                if (qp.equals(QP_PATTERN)) {
                    lineText = "Hit W now!";
                } else if (qp.equals(MICRO_QP_PATTERN)) {
                    lineText = "Hit alt + 0176 now!";
                }

            } else if (seg - chatBoxTypedTextLength < 0) {
                lineText = "Too far.";
            }

            overlayText.add(lineText);
            overlayTextColour.add(scaledPercentPixelsToW);
        }
    }

    /**
     * Refreshes the chatbox input and updates the recommendation overlay if it is enabled and last message includes a qp.
     */
    private void checkChatBoxUpdateOverlay() {
        if (lastMessageIncludesQP && config.showOverlay()) {
            refreshChatBoxTypedText();
            updateOverlayText();
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        checkChatBoxUpdateOverlay();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (isAltPressed) altBuffer += e.getKeyChar();
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            altBuffer = "";
            isAltPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            isAltPressed = false;
        }
        checkChatBoxUpdateOverlay();
    }
}
