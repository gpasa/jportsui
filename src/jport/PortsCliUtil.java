package jport;

import static jport.common.CliUtil.UNIX_BIN_BASH;
import static jport.common.CliUtil.BASH_OPT_C;
//
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jport.PortsConstants.EPortMark;
import jport.PortsConstants.EPortStatus;
import jport.common.CliUtil;
import jport.common.StringsUtil_;
import jport.common.Util;
import jport.type.CliPortInfo;
import jport.type.Portable;


/**
 * Actual Port status from CLI.
 * Ex. root CLI -> /bin/bash -c echo "*password*" | sudo -S ls -shakl /Library ;
 *
 * @author sbaber
 */
public class PortsCliUtil
{
    static private enum ECmd
            { VERSION // cli ports version
            , SYNC       ( true ) // update ports tree
            , SELFUPDATE ( true ) // update macPorts software and ports tree
            , ECHO // print
            , CONTENTS // files installed by the port
            , CLEAN // removes distribution, working, and/or log files
            ;
                    private ECmd() { this( false ); }
                    private ECmd( final boolean needAdmin ) { fNeedAdmin = needAdmin; }
                    final private boolean fNeedAdmin;
                    String _() { return this.name().toLowerCase(); }
            }

    static final private Thread DEAD_THREAD = new Thread( new Runnable() { @Override public void run() {} }, "DEAD_THREAD" );

    static final private String _PORT_BIN_PATH = "/opt/local/bin/port"; //... this is non-portable, use "which port" command instead
    static final public boolean HAS_PORT_CLI = new File( _PORT_BIN_PATH ).exists();

    static
    {}

    private PortsCliUtil() {}

    static private String _first( final String[] lines )
    {
        return ( lines.length != 0 ) ? lines[ 0 ] : "";
    }

    static public Thread cliTest( final CliUtil.Listener listener )
    {
        return ( HAS_PORT_CLI )
                ? CliUtil.forkCommand( listener, "list", "installed" ) // "locate Portfile" ~= 4,500, "locate perl" ~= 6,200 "locate java" ~= 54,000 "locate /" ~850,000
                : CliUtil.forkCommand( listener, "ping", "-n ", "4", "localhost" ); // waits a sec on Windows
    }

    static public String cliPortVersion()
    {
        if( HAS_PORT_CLI == false ) return "NOT AVAILABLE";

        return _first( CliUtil.executeCommand( _PORT_BIN_PATH, ECmd.VERSION._() ) );
    }

//... UNTESTED, needs root/admin to work, maybe sudoer file change walkthrough?
//... or run "sudo java -jar ..." with from CLI script asks user automagically?
        // "bash -c port" puts .exec() into port's interactive mode.
//... pass-in users Admin password, might need some sort of script or see 'man expect'

    /**
     *
     * @param port
     * @return files that were installed by the port
     */
    static synchronized public String[] cliFileContents( final Portable port )
    {
        if( HAS_PORT_CLI == false ) return StringsUtil_.NO_STRINGS;

        return CliUtil.executeCommand( _PORT_BIN_PATH, ECmd.CONTENTS._(), port.getName() );
    }

    /**
     * Full accounting avoids asking for All ports or Uninstalled ports as
     * these are assumed from the "PortIndex" parsing.
     * Note: Inefficient but I do not know a way to get all status attributes for each installed port, see "man port"
     *
     * @return as reported by the CLI "port echo installed" all of which are type CliPort
     */
    static Map<EPortStatus,Set<CliPortInfo>> cliAllStatus()
    {
        final Map<EPortStatus,Set<CliPortInfo>> status_to_InfoSet_Map = new EnumMap<EPortStatus, Set<CliPortInfo>>( EPortStatus.class );

        if( HAS_PORT_CLI == false )
        {   // non-Ports environment, needs to be installed
            for( final EPortStatus statusEnum : EPortStatus.VALUES )
            {
                final Set<CliPortInfo> emptySet = Collections.emptySet();
                status_to_InfoSet_Map.put( statusEnum, emptySet );
            }
            return status_to_InfoSet_Map;
        }

        for( final EPortStatus statusEnum : EPortStatus.VALUES )
        {
            switch( statusEnum )
            {
                case ALL : // fall-thru
                case UNINSTALLED : // do not run CLI on these
                    {   final Set<CliPortInfo> emptySet = Collections.emptySet();
                        status_to_InfoSet_Map.put( statusEnum, emptySet );
                    }   break;

                default :
                    {   status_to_InfoSet_Map.put( statusEnum, cliEcho( statusEnum ) );
                        break;
                    }
            }
        }

        return status_to_InfoSet_Map;
    }

    /**
     * Requests package info from the Ports CLI.
     *
     * @param statusEnum type of port name, version and variant information to echo
     * @return as reported by the CLI
     */
    static private Set<CliPortInfo> cliEcho( final EPortStatus statusEnum )
    {
        final Set<CliPortInfo> set = new HashSet<CliPortInfo>();

        final String portStatus = statusEnum.name().toLowerCase(); // a psuedo-name
        final String[] lines = CliUtil.executeCommand( _PORT_BIN_PATH, ECmd.ECHO._(), portStatus );

        for( final String untrimmedLine : lines )
        {   // CLI reported information
            final String line = untrimmedLine.trim(); // required
            final int p = line.indexOf( '@' ); // installed version
            final String cliPortName = line.substring( 0, p ).trim();

            final int q = line.indexOf( '+' ); // installed variants
            final String cliVersionRevision = ( q != -1 ) ? line.substring( p + 1, q ) : line.substring( p + 1 );
            final String multiVariant = ( q != -1 ) ? line.substring( q + 1 ) : "";
            final String[] variantSplits = multiVariant.split( "[+]" ); // on literal '+'
            final String[] cliVariants;
            if( variantSplits.length == 0 || variantSplits[ 0 ].isEmpty() == true )
            {
                cliVariants = StringsUtil_.NO_STRINGS;
            }
            else
            {
                cliVariants = variantSplits;
                Arrays.sort( cliVariants ); // must sort for .deepEquals()
            }

            // extract installed revision number after underscore char
            final int r = cliVersionRevision.lastIndexOf( '_' );

            final String cliVersion = ( r != Util.INVALID_INDEX )
                    ? cliVersionRevision.substring( 0, r )
                    : cliVersionRevision;
            
            final String cliRevision = ( r != Util.INVALID_INDEX )
                    ? cliVersionRevision.substring( r + 1 )
                    : "0";
            
            final CliPortInfo cpi = new CliPortInfo
                    ( cliPortName.intern()
                    , cliVersion.intern()
                    , cliRevision.intern()
                    , cliVariants
                    );
            set.add( cpi );
        }

        if( PortsConstants.DEBUG ) { System.out.println( statusEnum.name() +'='+ set.size() ); }

        return set;
    }

    /**
     *
     * @param <S>
     * @param isSimulated 'true' for dry-run testing
     * @param map
     * @return
     */
    static public <S extends Set<? extends Portable>> String getApplyMarksCli( final boolean isSimulated, final Map<EPortMark,S> map )
    {
        final StringBuilder sb = new StringBuilder();
        for( final Map.Entry<EPortMark,S> entry : map.entrySet() )
        {
            final EPortMark portMark = entry.getKey(); // alias
            final S portSet = entry.getValue(); // alias

            if( portMark.provideIsVisible() == true && portSet.isEmpty() == false )
            {
                final String DRY_RUN =  "-y -v -t "; // Dr[y] Run, [v]erbose, [t]est deps
                sb  .append( "sudo port" )
                    .append( ' ' )
                    .append( portMark.getCliOption() )
                    .append( ' ' )
                    .append( ( isSimulated == true ) ? DRY_RUN : "" )
                    .append( portMark.getCliCommand() );

                for( final Portable port : portSet )
                {   // prefixes a space and quotes are required to disambiguate from the "sudo" port itself
                    final String nameVariant = TheApplication.INSTANCE.getPortsCatalog().getPortsVariants().getNameVariant( port );
                    sb.append( ' ' ).append( '\"' ).append( nameVariant ).append( '\"' );
                }

                sb.append( " ; " );
            }
        }

        return sb.toString();
    }

    /**
     * Let CLI "-ru" option resolve any Activate, Install, Upgrade deps according to the CLI port tool.
     *
     * @param <S>
     * @param map the user's marked intentions
     * @param isSimulated 'true' for dry-run test
     * @param listener
     * @param password
     * @return 'null' if no ports
     */
    static synchronized public <S extends Set<? extends Portable>> Thread cliApplyMarks
            ( final String           password
            , final boolean          isSimulated
            , final Map<EPortMark,S> map
            , final CliUtil.Listener listener
            )
    {
        if( HAS_PORT_CLI == false ) return DEAD_THREAD;

        final String cliCmd = getApplyMarksCli( isSimulated, map );
        final String bashIt = cliCmd.replace( "sudo port", "echo \""+ password +"\" | sudo -S "+ _PORT_BIN_PATH );
        return CliUtil.forkCommand( listener, UNIX_BIN_BASH, BASH_OPT_C, bashIt );
    }

    /**
     * Updates the MacPorts CLI software itself, and performs a Port tree rsync as a side-effect.
     *
     * @param password
     * @param listener
     * @return 'null' if no ports
     */
    static synchronized public Thread cliUpdateMacPortsItself( final String password, final CliUtil.Listener listener )
    {
        if( HAS_PORT_CLI == false ) return DEAD_THREAD;

        final String portCmd = "-v "+ ECmd.SELFUPDATE._();
        final String bashIt = "echo \""+ password +"\" | sudo -S "+ _PORT_BIN_PATH +' '+ portCmd + " ; ";
        return CliUtil.forkCommand( listener, UNIX_BIN_BASH, BASH_OPT_C, bashIt ); // ok!
    }

    /**
     * Port bin will call rsync to update the port tree and updates the PortIndex file.
     *
     * @param listener
     * @param password
     * @return 'null' if no ports
     */
    static synchronized public Thread cliSyncUpdate( final String password, final CliUtil.Listener listener )
    {
        if( HAS_PORT_CLI == false ) return DEAD_THREAD;

        final String portCmd = "-v "+ ECmd.SYNC._();
        final String bashIt = "echo \""+ password +"\" | sudo -S "+ _PORT_BIN_PATH +' '+ portCmd + " ; ";
        return CliUtil.forkCommand( listener, UNIX_BIN_BASH, BASH_OPT_C, bashIt ); // ok!
    }

    /**
     * Cleans all installed Ports of distribution files, working files, and logs.
     * Needs / Was supposed to Removes all inactive Ports also.
     *
     * @param password
     * @param listener
     * @return 'null' if no ports
     */
    static synchronized public Thread cliCleanInstalledRemoveInactive( final String password, final CliUtil.Listener listener )
    {
        if( HAS_PORT_CLI == false ) return DEAD_THREAD;

        final String portCmd = "-u -p "+ ECmd.CLEAN._() +" --all "+ EPortStatus.INSTALLED.name().toLowerCase();
        final String bashIt = "echo \""+ password +"\" | sudo -S "+ _PORT_BIN_PATH +' '+ portCmd +" ; ";
        return CliUtil.forkCommand( listener, UNIX_BIN_BASH, BASH_OPT_C, bashIt );
    }
}
