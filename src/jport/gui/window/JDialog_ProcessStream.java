package jport.gui.window;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import jport.common.CliUtil.Adapter;
import jport.common.CliUtil.Listener;
import jport.common.Notification.OneArgumentListenable;
import jport.common.StringsUtil_;
import jport.common.gui.FocusedButtonFactory;
import jport.common.gui.JScrollPaneFactory_;
import jport.common.gui.JScrollPaneFactory_.EScrollPolicy;
import jport.gui.JListModel_Array;
import jport.gui.TheUiHolder;


/**
 * Incrementally displays output from the CLI process.
 *
 * @author sbaber
 */
@SuppressWarnings("serial")
public class JDialog_ProcessStream extends JDialog
{
    static
    {}

    /**
     * Slower incremental output from CLI process.
     *
     * @param dialogTitle non-HTML
     * @param cliable the port command thread to .start()
     * @param resultCodeListenable permits intelligence regarding failed port CLI commands, can be 'null'
     */
    public JDialog_ProcessStream
            ( final String dialogTitle
            , final Cliable cliable
            , final OneArgumentListenable<Integer> resultCodeListenable
            )
    {
        this( false, dialogTitle, cliable, resultCodeListenable );
    }

    /**
     *
     *
     * @param isLotsOfFastOutput use 'true' if you expect millions of lines of quickly produced output, ex. CLI -> "locate /"
     * @param dialogTitle non-HTML
     * @param cliable the port command thread to .start()
     * @param resultCodeListenable permits intelligence regarding failed port CLI commands, can be 'null'
     */
    JDialog_ProcessStream
            ( final boolean isLotsOfFastOutput
            , final String dialogTitle
            , final Cliable cliable
            , final OneArgumentListenable<Integer> resultCodeListenable
            )
    {
        super
            ( TheUiHolder.INSTANCE.getMainFrame() // stay on top
            , dialogTitle
            , ModalityType.APPLICATION_MODAL
            );

        this.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );// required
        ((JPanel)this.getContentPane()).setBorder( BorderFactory.createEmptyBorder( 10, 10, 5, 10 ) ); // T L B R

        final JList jl = new JList();
        jl.setFont( new Font( Font.MONOSPACED, Font.BOLD, 12 ) );
        jl.setForeground( Color.GREEN.brighter() );
        jl.setBackground( Color.BLACK );
        jl.setLayoutOrientation( JList.VERTICAL );
        jl.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        jl.setVisibleRowCount( -1 ); // all

        final AbstractButton ab_Cancel = FocusedButtonFactory.create( "Cancel", "" );
        ab_Cancel.setEnabled( false );

        final JProgressBar jProgress = new JProgressBar();
        jProgress.setIndeterminate( true );

        // console
        final Listener listener = ( isLotsOfFastOutput == true )
                ?   new LateListening( this, jl, jProgress, ab_Cancel, resultCodeListenable )
                :   new LiveListening( this, jl, jProgress, ab_Cancel, resultCodeListenable );

        final Thread thread = cliable.provideExecutingCommandLineInterfaceThread( listener );
        if( thread != null )
        {
            final JPanel southPanel = new JPanel( new GridLayout( 1, 0 ) );
            southPanel.add( jProgress );
            southPanel.add( ab_Cancel );

            final JScrollPane jsp = JScrollPaneFactory_.create( jl, EScrollPolicy.VERT_AS_NEEDED__HORIZ_NONE );

            // assemble rest of gui in this AWT thread
            this.add( Box.createHorizontalStrut( 800 ), BorderLayout.NORTH );
            this.add( Box.createVerticalStrut( 600 )  , BorderLayout.EAST );
            this.add( jsp                             , BorderLayout.CENTER );
            this.add( southPanel                      , BorderLayout.SOUTH );

            this.pack();
            this.setLocationRelativeTo( TheUiHolder.INSTANCE.getMainFrame() );

            // not working correctly to Cancel cli
    //        ab.addActionListener( new ActionListener() // anonymous class
    //                {   @Override public void actionPerformed( ActionEvent e )
    //                    {   thread.interrupt();
    //                        try
    //                        {   // Cancel
    //                            thread.join( 2000 );
    //                        }
    //                        catch( InterruptedException ex )
    //                        {}
    //                    }
    //                } );
        }
        else
        {   // no Ports binary to run
            this.dispose();
        }
    }


    // ================================================================================
    /**
     * Run any CLI command with our private listener.
     */
    static public interface Cliable
    {
        abstract public Thread provideExecutingCommandLineInterfaceThread( final Listener listener );
    }


    // ================================================================================
    /**
     * Abstract parent class implementation for listening.
     */
    static abstract private class AListening extends Adapter
        implements
              Listener
            , ActionListener
    {
        final private OneArgumentListenable<Integer> fResultCodeCompletionCallback;
        final private Window fWindowToClose;
        final private JProgressBar jProgressBar;
        final private AbstractButton ab_Done;

        final JList jList;

        /**
         * @param windowToClose
         * @param jList
         * @param abDone
         * @param resultCodeListenable can be 'null'
         */
        private AListening
                ( final Window windowToClose
                , final JList jList
                , final JProgressBar jpb
                , final AbstractButton abDone
                , final OneArgumentListenable<Integer> resultCodeListenable
                )
        {
            this.fWindowToClose = windowToClose;
            this.jList = jList;
            this.jProgressBar = jpb;
            this.ab_Done = abDone;

            this.fResultCodeCompletionCallback = ( resultCodeListenable != null )
                    ? resultCodeListenable
                    : new OneArgumentListenable<Integer>() { @Override public void listen( Integer event ) {} }; // anonymous class

            abDone.addActionListener( this );
        }

        @Override public void cliProcessException( Exception ex )
        {
            final String[] lines = StringsUtil_.toStrings( ex.getStackTrace() );

            final JLabel jLabel_Exception = new JLabel();
            jLabel_Exception.setForeground( Color.RED );
            jLabel_Exception.setText( "<HTML>"+ StringsUtil_.concatenate( "<BR>", lines ) );
            fWindowToClose.add( jLabel_Exception, BorderLayout.WEST );
        }

        /**
         * Maybe a lengthy operation depending on the passed in Runnable.
         *
         * @param resultCode
         * @param cliOutputLines
         * @param cliErrorLines
         */
        @Override public void cliProcessCompleted( final int resultCode, final String[] cliOutputLines, final String[] cliErrorLines )
        {
            if( cliErrorLines.length != 0 )
            {
                final JLabel jLabel_Error = new JLabel();
                jLabel_Error.setForeground( Color.RED );
                jLabel_Error.setOpaque( true ); // BorderLayout functions as a NullLayout inside each compass constraint
                jLabel_Error.setText( "<HTML>"+ StringsUtil_.concatenate( "<BR>", cliErrorLines) );
                fWindowToClose.add( jLabel_Error, BorderLayout.NORTH );
            }

            jProgressBar.setVisible( false );

            ab_Done.setText( "Close" );
            ab_Done.setEnabled( true );
            ab_Done.requestFocusInWindow(); // hilite it

            final Thread postProcessCompletionThread = new Thread
                    ( new Runnable() // anonymous class
                            {   @Override public void run()
                                {   // cause notification
                                    fResultCodeCompletionCallback.listen( resultCode ); // autobox
                                }
                            }
                    , this.getClass().getCanonicalName()
                    );
            postProcessCompletionThread.start(); // runs completion in another thread so Swing can update the pending JList paint
        }

        @Override public void actionPerformed( ActionEvent e )
        {
            fWindowToClose.dispose();
        }

        void scrollToEnd()
        {
            final int count = jList.getModel().getSize();
            jList.ensureIndexIsVisible( count - 1 ); // scroll to end
            ab_Done.setToolTipText( "<HTML>Close this window<BR>lines="+ count );
        }
    }

    // ================================================================================
    /**
     * Incrementally updating display.
     * Used when expecting a slow deluge of output from an externally executing process.
     */
    static private class LiveListening extends AListening
    {
        static final private int SCROLL_MILLISEC = 100; // 10hz

        final private DefaultListModel fDefaultListModel = new DefaultListModel();
        final private List<String> fIncrementalLineList = new ArrayList<String>( 256 );

        private long mNextEpoch = 0L;

        /**
         * @param windowToClose
         * @param jList
         * @param abDone
         * @param resultCodeListenable can be 'null'
         */
        private LiveListening
                ( final Window windowToClose
                , final JList jList
                , final JProgressBar jpb
                , final AbstractButton abDone
                , final OneArgumentListenable<Integer> resultCodeListenable
                )
        {
            super
                ( windowToClose
                , jList
                , jpb
                , abDone
                , resultCodeListenable
                );

            jList.setModel( fDefaultListModel );
        }

        @Override public boolean cliProcessListenerNeedsSwingDeferment() { return false; }

        @Override public void cliProcessOutput( final String outputLine )
        {
            fIncrementalLineList.add( outputLine );

            if( System.currentTimeMillis() > mNextEpoch )
            {   //  avoid Swing GC thrash-fest
                final String[] lines = StringsUtil_.toStrings( fIncrementalLineList );
                fIncrementalLineList.clear();

                deferUpdate( lines );

                mNextEpoch = SCROLL_MILLISEC + System.currentTimeMillis();
            }
        }

        @Override public void cliProcessCompleted( final int resultCode, final String[] cliOutputLines, final String[] cliErrorLines )
        {
            deferUpdate( StringsUtil_.toStrings( fIncrementalLineList ) );
            fIncrementalLineList.clear();

            super.cliProcessCompleted( resultCode, cliOutputLines, cliErrorLines );
        }

        /**
         * Batch up the JList updates by deferring to Event Dispatch thread.
         *
         * @param lines producer-consumer queue
         */
        private void deferUpdate( final String[] lines )
        {
            SwingUtilities.invokeLater( new Runnable() // anonymous class
                    {   @Override public void run()
                        {   fDefaultListModel.ensureCapacity( lines.length + fDefaultListModel.getSize() );
                            for( final String line : lines )
                            {
                                fDefaultListModel.addElement( line );
                            }
                            scrollToEnd();
                        }
                    } );
        }
    }


    // ================================================================================
    /**
     * Completion Listening, no update until stream completes.
     * Used when expecting millions of lines of rapid output.
     * Naive implementations would cause a Swing GC thrash-fest to halt GUI updates.
     */
    static private class LateListening extends AListening
    {
        /**
         * @param windowToClose
         * @param jList
         * @param abDone
         * @param resultCodeListenable can be 'null'
         */
        private LateListening
                ( final Window windowToClose
                , final JList jList
                , final JProgressBar jpb
                , final AbstractButton abDone
                , final OneArgumentListenable<Integer> resultCodeListenable
                )
        {
            super
                ( windowToClose
                , jList
                , jpb
                , abDone
                , resultCodeListenable
                );
        }

        @Override public boolean cliProcessListenerNeedsSwingDeferment() { return false; }

        /**
         * Deferred to Event Dispatch thread.
         *
         * @param resultCode
         * @param cliOutputLines
         * @param cliErrorLines
         */
        @Override public void cliProcessCompleted( final int resultCode, final String[] cliOutputLines, final String[] cliErrorLines )
        {
            SwingUtilities.invokeLater( new Runnable() // anonymous class
                    {   @Override public void run()
                        {   final ListModel arrayListModel = new JListModel_Array<String>( cliOutputLines );
                            jList.setModel( arrayListModel );
                            scrollToEnd();

                            LateListening.super.cliProcessCompleted( resultCode, cliOutputLines, cliErrorLines ); // java ye be a strange beastie
                        }
                    } );
        }
    }
}