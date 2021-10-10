package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */

public class DictionaryConnection {

    private Socket socket;
    private DataOutputStream dos;
    private BufferedReader br;


    private static final int DEFAULT_PORT = 2628;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            // Connect to socket using host and port
            socket = new Socket(host, port);

            // Save input stream reader and output stream to class properties to use throughout the methods
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            dos = new DataOutputStream(socket.getOutputStream());

            // Read welcome message
            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message
            String code = "";
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();
                if (msg == null) {
                    throw new DictConnectionException("Received Empty Message");
                }

                if (Character.isDigit(msg.charAt(0))) {
                    // If the message string starts with number, that number is the response code
                    code = msg.substring(0,3);
                }

                // Check if code tells us to stop reading message
                // Will throw error if invalid code eg. starts with "5"
                stop = StopReadingFromDict(code);
            }
        } catch (Exception e) {
            // Any exceptions related to incorrect or invalid connection info provided should be caught here
            throw new DictConnectionException(e);
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            dos.writeBytes("QUIT\n");

            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message
            String code = "";
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (Character.isDigit(msg.charAt(0))) {
                    // If the message string starts with number, that number is the response code
                    code = msg.substring(0,3);
                }

                if (code.equals("221")) {
                    // This code in response to the "QUIT" command means we can safely close the socket.
                    socket.close();
                    stop = true;
                }
            }
        } catch (IOException e) {
            // Any extraneous IO exceptions should be caught here
            e.printStackTrace();
        }
        // Set socket and streams/readers to null
        socket = null;
        dos = null;
        br = null;
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        try {
            // Format arguments as command string
            String command = "DEFINE " + database.getName() + " \"" + word + "\"\n";
            dos.writeBytes(command);

            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message.
            // We also need a variable to save the definition read from the message string,
            // and a placeholder string variable for the dictionary name read from the message
            String code = "";
            String msg;
            boolean stop = false;
            Definition def = new Definition(word, database.getName());
            String realDictName = "";
            while(!stop) {
                msg = br.readLine();
                if (msg.length() > 1 && Character.isDigit(msg.charAt(0))) {
                    // If the message string starts with number, that number is the response code
                    code = msg.substring(0,3);

                    if (code.equals("151")) {
                        // Only when a new code is sent we want to check if we need to grab the dictionary name from the
                        // message.
                        // Eg. when the * or ! is specified we need to extract the dictName from the message
                        realDictName = getDictNameFromMsg(msg);
                        def = new Definition(word, realDictName);
                    }

                } else if (msg.length() > 1 && code.equals("151")) {
                    // This ELSE IF statement makes it so that the first line with the code is not added to the definition.
                    // Only append to definition when code is currently equal to 151 and the message is not empty.
                    def.appendDefinition(msg);
                } else if (msg.length() == 0) {
                    // This ELSE IF statement adds a new line when there is one.
                    def.appendDefinition(msg);
                }

                // Check if code tells us to stop reading message
                // Will throw error if invalid code eg. starts with "5"
                stop = StopReadingFromDict(code);

                if (msg.equals(".")){
                    // end of definition, add definition to definition set and reset placeholder definition variables
                    set.add(def);
                    def = new Definition(word, database.getName());
                    realDictName = "";
                }


            }

        } catch (IOException e) {
            // Any extraneous IO exceptions should be caught here
            throw new DictConnectionException();
        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();
        try {
            // Format arguments as MATCH command string
            String command = "MATCH " + database.getName() + " " + strategy.getName() + " ";
            command = command + Arrays.toString(DictStringParser.splitAtoms(word))
                    .replace("[", "\"")
                    .replace("]", "\"")
                    + "\n";
            dos.writeBytes(command);

            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message.
            String code = "";
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 1 && Character.isDigit(msg.charAt(0))) {
                    // If the message string starts with number, that number is the response code
                    code = msg.substring(0,3);

                } else if (!msg.equals(".")) {
                    // end of match, but not the end of message

                    if (msg.contains("\"")) {
                        // Find the index of the " character, which marks the start of the word to be matched
                        String theWord = msg.substring(msg.indexOf("\"")+1, msg.length() - 1);
                        set.add(theWord);
                    }
                }

                // Check if code tells us to stop reading message
                // Will throw error if invalid code eg. starts with "5"
                stop = StopReadingFromDict(code);

            }

        } catch (IOException e) {
            // Any extraneous IO exceptions should be caught here
            throw new DictConnectionException();
        }

        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            String command = "SHOW DB\n";
            dos.writeBytes(command);

            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message.
            String code;
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 3) {

                    if (Character.isDigit(msg.charAt(0))) {
                        // If the message string starts with number, that number is the response code
                        code = msg.substring(0, 3);

                        // Code 110 marks the beginning of db definition.
                        if (code.equals("110")) {
                            msg = br.readLine();
                        }

                        // Determine if this is the end of definitions.
                        // will throw error if invalid code eg. starts with "5"
                        stop = StopReadingFromDict(code);
                    }

                    if (msg.contains("\"")) {
                        // The " character marks the start of the db name and the next " marks the start of the description.
                        String name = msg.substring(0, msg.indexOf("\"")).trim();
                        String description = msg.substring(msg.indexOf("\"")+1, msg.length()-1);
                        databaseMap.put(name, new Database(name, description));
                    }
                }

            }

        } catch (IOException e) {
            // Any extraneous IO exceptions should be caught here
            throw new DictConnectionException();
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        try {
            String command = "SHOW STRATEGIES\n";
            dos.writeBytes(command);

            // Initialize variables for reading message to string, reading code from message string, and condition
            // variable to know when to stop reading the message.
            String code;
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 3) {

                    if (Character.isDigit(msg.charAt(0))) {
                        // If the message string starts with number, that number is the response code
                        code = msg.substring(0, 3);

                        // Code 110 marks the beginning of strategy
                        if (code.equals("111")) {
                            msg = br.readLine();
                        }

                        // Determine if this is the end of message.
                        // will throw error if invalid code eg. starts with "5"
                        stop = StopReadingFromDict(code);
                    }

                    if (msg.contains("\"")) {
                        // Use " characters as markers for string parsing
                        String name = msg.substring(0, msg.indexOf("\"")).trim();
                        String description = msg.substring(msg.indexOf("\"") + 1, msg.length() - 1);
                        set.add(new MatchingStrategy(name, description));
                    }
                }

            }

        } catch (IOException e) {
            // Any extraneous IO exceptions should be caught here
            throw new DictConnectionException();
        }

        //return databaseMap;

        return set;
    }

    /** Checks a code number in a string to determine if we can stop reading message from input reader
     *
     * @param currentCode 3 digit code retrieved from message
     * @return A boolean representing if we should stop reading from input reader
     * @throws DictConnectionException If code represents some problem with the connection.
     */
    private boolean StopReadingFromDict(String currentCode) throws DictConnectionException {
        //System.out.println("Code:" + currentCode );
        if (currentCode.startsWith("1")) {
            // There is text to follow
            return false;
        } else if (currentCode.startsWith("250")) {
            // this is the last message
            return true;
        } else if (currentCode.startsWith("220")) {
            //connection finished
            return true;
        } else if (currentCode.startsWith("4")) {
            // transient connection failure
            throw new DictConnectionException("Transient Negative Completion");
        } else if (currentCode.startsWith("50")) {
            throw new DictConnectionException("Syntax Error");
        } else if (currentCode.startsWith("53")) {
            throw new DictConnectionException("Access Denied");
        } else if (currentCode.startsWith("55")) {
            // Don't throw error, valid command, but for reasons, no matches
            return true;
        }

        return false;
    }

    /** Parses a dict name from a message string
     *
     * @param msg a line from a message reponse starting with a certain code
     * @return A String representing a dict name
     */
    private String getDictNameFromMsg(String msg) {
        int firstQuoteIndex = msg.indexOf("\"");
        int secondQuoteIndex = msg.indexOf("\"", firstQuoteIndex+1);
        int endIndex = msg.indexOf(" ", secondQuoteIndex+2);
        String dictName = msg.substring(secondQuoteIndex+2, endIndex);
        return dictName;
    }
}
