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
                    code = msg.substring(0,2);
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

        // Send first message
        try {
            //String command = "HELP \n"; // This is for testing.
            String command = "DEFINE " + database.getName() + " " + word + " \n";
            dos.writeBytes(command);

            // Read welcome message
            String code;
            String msg;
            String mergedMsg = "";
            Definition def = new Definition(word, database.getName());
            boolean stop = false;
            while(!stop) {
                msg = br.readLine();

                if (msg.length() > 1 && Character.isDigit(msg.charAt(0))) {
                    // String starts with number, contains code
                    code = msg.substring(0,3);

                    // Each 151 entry refers to a new definition.
                    if(code.equals("151") ) {
                        set.add(def);
                        def = new Definition(word, database.getName());
                    }

                    // Determine if this is the end of definitions.
                    //stop = StopReadingFromDict(code);
                    else if(code.equals("250") ) {
                        stop = true;
                    }

                    else if(code.startsWith("5")) {
                        throw new DictConnectionException();
                    }
                }


                //System.out.println(msg + "ENDL HERE"); // for testing only.

                def.appendDefinition(msg);


            }
            //System.out.println("Done connecting"); //
            //set.add(def);

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

        // TODO Add your code here

        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        // TODO Add your code here

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here

        return set;
    }

    private boolean StopReadingFromDict(String currentCode) {
        if (!currentCode.isEmpty() && currentCode.startsWith("1")) {
            // There is text to follow
            return false;
        } else {
            // this is the last message
            return true;
        }
    }
}
