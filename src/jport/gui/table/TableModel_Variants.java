package jport.gui.table;

import jport.TheApplication;
import jport.common.Elemental;
import jport.common.Elemental.EElemental;
import jport.common.Providers_.ClassProvidable;
import jport.common.Providers_.EnabledProvidable;
import jport.common.Providers_.WidthProvidable;
import jport.common.StringsUtil_;
import jport.common.gui.AEnumTableModel_Array;
import jport.ports.PortsVariants;
import jport.type.Portable;


/**
 * UI for changing desired variants.
 *
 * @author sbaber
 */
@SuppressWarnings("serial")
public class TableModel_Variants extends AEnumTableModel_Array<String,TableModel_Variants.EColumn>
    implements Elemental.Listenable<Portable>
{
    static enum EColumn implements WidthProvidable, EnabledProvidable, ClassProvidable
            { _           (  18 ) { @Override public Class<?> provideClass() { return Boolean.class; } @Override public boolean provideIsEnabled() { return true; } }  // check box
            , VARIANT     (  -1 )
            ;
                    private EColumn( final int width ) { fWidth = width; }
                    private int fWidth;
                    @Override public int provideWidth() { return fWidth; }
                    @Override public boolean provideIsEnabled() { return false; }
                    @Override public Class<?> provideClass() { return Object.class; }
            }

    static
    {}

    final private PortsVariants fPortsVariants = TheApplication.INSTANCE.getPortsCatalog().getPortsVariants(); // alias

    final private boolean fIsAssignmentLocked;

    /** Mutable for .actionPerformed() and follows table selection via .notify().  Must start with 'null' */
    transient private Portable mAssignedPort = null;


    /**
     *
     * @param assignedPort is the target model.  Use Portable.NONE to signal driven by user's table selection.
     */
    public TableModel_Variants( final Portable assignedPort )
    {
        super
            ( String.class
            , EEditable.ENABLE
            , ERowSelection.NO_SELECTION
            , EColorize.DISABLE
            , EColumn.values()
            );

        if( assignedPort == null ) throw new NullPointerException();

        fIsAssignmentLocked = assignedPort != Portable.NONE;

        this.toggelSortColumn( EColumn.VARIANT );

        // listener
        TheApplication.INSTANCE.getCrudNotifier().addListener( this ); // automatically calls .notify() and updates mAssignedPort conforming the view
    }

    private void setPort( final Portable port )
    {
        if( port == Portable.NONE )
        {
            this.setRows( StringsUtil_.NO_STRINGS );
        }
        else
        {
            this.setRows( port.getVariants().clone() );
            fireTableDataChanged(); // update check boxes
        }
    }

    /**
     * Follows main table selection or initializes the locked port assignment.
     *
     * @param elemental action
     * @param port of marking view
     */
    @Override public void notify( final EElemental elemental, final Portable port )
    {
        switch( elemental )
        {
            case RETRIEVED :
                {   if( fIsAssignmentLocked == false || mAssignedPort == null )
                    {
                        mAssignedPort = port;
                        setPort( port );
                    }
                }   break;

            case UPDATED :
                {   if( mAssignedPort == port )
                    {   // filtered out non-related updates
                        setPort( port );
                    }
                }   break;
        }
    }

    /**
     *
     * @param variant row
     * @param columnEnum
     * @return placed in table
     */
    @Override public Object getValueOf( final String variant, final EColumn columnEnum )
    {
        switch( columnEnum )
        {   
            case _       : return fPortsVariants.isApplicableVariant( mAssignedPort, variant );
            case VARIANT : return variant;

            default : return "ERR";
        }
    }

    /**
     *
     * @param value to update model with
     * @param variant row
     * @param columnEnum
     */
    @Override public void setValueOf( final Object value, final String variant, final EColumn columnEnum )
    {
        switch( columnEnum )
        {
            case _ :
                {   final boolean isInstalling = ((Boolean)value).booleanValue();
                    fPortsVariants.setVariant( mAssignedPort, variant, isInstalling );
                }   break;

            default: break;
        }
    }
}
