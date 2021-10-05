package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;
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
            System.out.println("DictionaryConnect Started");
            socket = new Socket(host, port);
            dis = new DataInputStream(socket.getInputStream());
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            dos = new DataOutputStream(socket.getOutputStream());

            // Read welcome message
            String code = "";
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();
                if (Character.isDigit(msg.charAt(0))) {
                    // String starts with number, contains code
                    code = msg.substring(0,3);
                }
                stop = StopReadingFromDict(code);

                System.out.println(msg);
            }
            System.out.println("Done connecting");
        } catch (UnknownHostException e) {
            throw new DictConnectionException(e);
        } catch (IOException e) {
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
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = null;
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

        // TODO: format of result. Put the database from result in the define.

        // try "DEFINE fd-eng-rom computer" to see the problem. // This problem has solved.

        // Send first message
        try {
            //String command = "HELP \n"; // This is for testing.
            String command = "DEFINE " + database.getName() + " " + word + " \n";
            dos.writeBytes(command);

            // Read welcome message
            String code = "";
            String msg;
            String mergedMsg = "";
            Definition def = new Definition(word, database.getName());
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();
                System.out.println(msg + "ENDL, size = " + msg.length() + " place of \":" + msg.indexOf("\"")); // for testing only.
                if (msg.length() > 1 && Character.isDigit(msg.charAt(0))) {
                    // String starts with number, contains code
                    code = msg.substring(0,3);

                    /*
                            NO LONGER IN USE.
                    //Each 151 entry refers to a new definition.
                    //if(code.equals("151") ) {
                    //    def = new Definition(word, database.getName());
                    }
                    */
                }

                // will throw error if invalid code eg. starts with "5"
                stop = StopReadingFromDict(code);

                //if (def != null && code.equals("151")) {
                    // Build definition
                //    def.appendDefinition(msg);
                //}

                if (msg.equals(".")){ //&& (code.equals("151") || code.equals("250"))) {
                    // end of definition, create new (clone) definition, and reset definition builder object
                    System.out.println("New Def!");
                    //Definition defToReturn = new Definition(word, database.getName());
                    //defToReturn.setDefinition(def.getDefinition());
                    set.add(def);
                    def = new Definition(word, database.getName());
                }

                if (!code.equals("150")) {
                    def.appendDefinition(msg);
                }
            }

        } catch (IOException e) {
            //e.printStackTrace(); // for testing only.
            //throw DictConnectionException If the connection was interrupted.
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

        // TODO: Fix the bug:
        //  501: use * as DB, Match Prefix as STRAT, type bunny, select bunny hug
        try {
            //String command = "HELP \n"; // This is for testing.
            //String command = "MATCH " + database.getName() + " " + strategy.getName() + " " + word + " \n";
            String command = "MATCH " + database.getName() + " " + strategy.getName() + " ";
            command = command + Arrays.toString(DictStringParser.splitAtoms(word)) + " \n";
            dos.writeBytes(command);

            // Read welcome message
            String code = "";
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();
                //System.out.println(msg + "ENDL, size = " + msg.length() + " place of \":" + msg.indexOf("\"")); // for testing only.
                if (msg.length() > 1 && Character.isDigit(msg.charAt(0))) {
                    // String starts with number, contains code
                    code = msg.substring(0,3);

                } else if (!msg.equals(".")) {  // Not the last line

                    if (msg.contains("\"")) {   // find the "
                        String theWord = msg.substring(msg.indexOf("\"")+1, msg.length() - 1);
                        set.add(theWord);
                    }
                }

                // will throw error if invalid code eg. starts with "5"
                stop = StopReadingFromDict(code);

            }

        } catch (IOException e) {
            //e.printStackTrace(); // for testing only.
            //throw DictConnectionException If the connection was interrupted.
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
            //System.out.println("getDatabaseList :)");
            //String command = "HELP \n"; // This is for testing.
            String command = "SHOW DB\n";
            dos.writeBytes(command);

            // Read welcome message
            String code;
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 3) {

                    if (Character.isDigit(msg.charAt(0))) {
                        // String starts with number, contains code
                        code = msg.substring(0, 3);

                        // Code 110 reminds the beginning of db definition.
                        if (code.equals("110")) {
                            msg = br.readLine();
                        }

                        // Determine if this is the end of definitions.
                        // will throw error if invalid code eg. starts with "5"
                        stop = StopReadingFromDict(code);
                    }

                    //System.out.println(msg + "ENDL, size = " + msg.length() + "place of \":" + msg.indexOf("\"")); // for testing only.
                    if (msg.contains("\"")) {
                        String name = msg.substring(0, msg.indexOf("\""));
                        String description = msg.substring(msg.indexOf("\""), msg.length() - 2);
                        databaseMap.put(name, new Database(name, description));
                    }
                }

            }
            //System.out.println("Done connecting"); //
            //set.add(def);

        } catch (IOException e) {
            //e.printStackTrace(); // for testing only.
            //throw DictConnectionException If the connection was interrupted.
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
            //System.out.println("getDatabaseList :)");
            //String command = "HELP \n"; // This is for testing.
            String command = "SHOW STRATEGIES\n";
            dos.writeBytes(command);

            // Read welcome message
            String code;
            String msg;
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 3) {

                    if (Character.isDigit(msg.charAt(0))) {
                        // String starts with number, contains code
                        code = msg.substring(0, 3);

                        // Code 111 reminds the beginning of db definition.
                        if (code.equals("111")) {
                            msg = br.readLine();
                        }

                        // Determine if this is the end of definitions.
                        // will throw error if invalid code eg. starts with "5"
                        stop = StopReadingFromDict(code);
                    }

                    //System.out.println(msg + "ENDL, size = " + msg.length() + "place of \":" + msg.indexOf("\"")); // for testing only.
                    if (msg.contains("\"")) {
                        String name = msg.substring(0, msg.indexOf("\""));
                        //System.out.print ("The Name: " + name);
                        String description = msg.substring(msg.indexOf("\"") + 1, msg.length() - 1);
                        //System.out.println ("\\The description: " + description);
                        set.add(new MatchingStrategy(name, description));
                    }
                }

            }
            //System.out.println("Done connecting"); //
            //set.add(def);

        } catch (IOException e) {
            //e.printStackTrace(); // for testing only.
            //throw DictConnectionException If the connection was interrupted.
            throw new DictConnectionException();
        }

        //return databaseMap;

        return set;
    }

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
        } else if (currentCode.startsWith("5")) {
            throw new DictConnectionException();
        }
        return false;
    }
}
