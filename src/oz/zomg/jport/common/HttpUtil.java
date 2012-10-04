package oz.zomg.jport.common;

import java.awt.Desktop;
import java.awt.Image;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import oz.zomg.jport.common.CachedUriContent.UriContentCacheable;
import oz.zomg.jport.common.Interfacing_.Targetable;


/**
 * Utilities for HTTP, URL, and URI.
 *
 * @author <SMALL>Copyright 2012 by Stephen Baber
 * &nbsp; <a rel="license" href="http://creativecommons.org/licenses/by-sa/3.0/deed.en_US">
 * <img alt="Creative Commons License" style="border-width:0" src="http://i.creativecommons.org/l/by-sa/3.0/80x15.png" /></a><br />
 * This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-sa/3.0/deed.en_US">
 * Creative Commons Attribution-ShareAlike 3.0 Unported License</a>.</SMALL>
 */
public class HttpUtil
{
    static final private boolean             _IS_LOGO_SPEC_W3C = true;
    static final private int                 _CONNECTION_TIME_OUT_MILLISEC = 9 * 1000; // 9 secs.
    static final private Thread_Worker       _HTTP_WORKER_THREAD = new Thread_Worker( HttpUtil.class.getSimpleName() );
    static final private UriContentCacheable _URI_CONTENT_CACHE = CachedUriContent.getInstance( true );

    static
    {
        _HTTP_WORKER_THREAD.start(); // sequentializes work as a FIFO, this behavior may not be desired
    }

    private HttpUtil() {}

    static public void clearCache()
    {
        _URI_CONTENT_CACHE.clearAll();
    }

    /**
     * Simple example.
     *
     * @param uri content location
     * @param contentTargetable content from uri
     */
    static private void retrieveConcurrently( final URI uri, final Targetable<UriContent> contentTargetable )
    {
        _HTTP_WORKER_THREAD.offer( new Runnable() // anonymous class
                {   @Override public void run()
                    {   final UriContent uriContent = retrieve( uri );
                        contentTargetable.target( uriContent );
                    }
                } );
    }

    /**
     * Retrieve web site's logo as ".../favicon.ico" (OR favicon.png OR favicon.gif?)
     * @link "http://www.w3.org/2005/10/howto-favicon"
     *
     * @param domain web address, ex. "http://www.gnu.org"
     * @param imageTargetable call back with domain's avatar image or 'null' if none, generally 16x16 pixels
     */
    static public void retrieveLogoConcurrently
            ( final String domain
            , final Targetable<Image> imageTargetable
            )
    {
        _HTTP_WORKER_THREAD.offer( new Runnable() // anonymous class
                {   @Override public void run()
                    {   final Image image = retrieveLogo( domain );
                        imageTargetable.target( image );
                    }
                } );
    }

    /**
     * Non-concurrent implementation.
     *
     * @param domain name of web site avatar icon
     * @return might be 'null'
     */
    static private Image retrieveLogo( final String domain )
    {
        // use the old-school way first since this is highly likely to succeed and is low-bandwidth, ex. "http://some.domain.org/favicon.ico"
        Image image = retrieveImage( domain +"/favicon.ico" ); // *BLOCKS*

        if( image == null && _IS_LOGO_SPEC_W3C == true )
        {   // use the official mechanism described by W3C "http://www.w3.org/2005/10/howto-favicon"
            final String homepageContent = retrieveHomepage( domain ); // *BLOCKS*
            if( homepageContent != null )
            {   // lame, no Document Object Model
                final String ciHomepage = homepageContent.toLowerCase(); // case insensitized
                final String faviconTag = "<link rel=\"icon\""; // also should try <link rel="shortcut icon" href...
                final int tagIndex = ciHomepage.indexOf( faviconTag );

                if( tagIndex != Util.INVALID_INDEX )
                {   // has <LINK REL="icon" ...> tag in the <HEAD>
                    final String faviconAttr = " href=\"";
                    final int attrIndex = ciHomepage.indexOf( faviconAttr, tagIndex );
                    if( attrIndex != Util.INVALID_INDEX )
                    {   // found HERF="..." attribute
                        final int begin = attrIndex + faviconAttr.length();
                        final int end = homepageContent.indexOf( '\"', begin );
                        final String faviconName = homepageContent.substring( begin, end );
                        final String faviconLocation = ( faviconName.startsWith( "http" ) == false )
                                ? domain + ( ( faviconName.startsWith( "/" ) ) ? "" : '/' ) + faviconName
                                : faviconName;
                        image = retrieveImage( faviconLocation ); // *BLOCKS*
                    }
                    // else no HREF= attribute
                }
                // else no tag <LINK REL="icon" ...> tag in the <HEAD>
            }
            // else no homepage
        }

        return image;
    }

    /**
     * Transparent ".ICO" will fail, bug is not fully fixed by JDK7.07
     *
     * @param imageLocation
     * @return 'null' if 404 or not an image
     */
    static private Image retrieveImage( final String imageLocation )
    {
        final URI uri = URI.create( imageLocation );
        final UriContent uriContent = retrieve( uri );
        return ( uriContent.fIs404 == false  )
                ? ImageUtil_.parseImage( imageLocation, uriContent.fContentBytes )
                : null;
    }

    /**
     *
     * @param domain
     * @return  web page's index page content
     */
    static private String retrieveHomepage( final String domain )
    {
        final URI uri = URI.create( domain );
        final UriContent uriContent = retrieve( uri );
        return ( uriContent.fIs404 == false  )
                ? new String( uriContent.fContentBytes ) //... lame, no encoding xlation
                : null;
    }

    /**
     * Cache and absorb IOExceptions while retrieving URI content.
     * If you need no caching or the exception, then invoke .readFullyBytes()
     *
     * @param uri
     * @return content bytes @ URI or 'null' if 404
     */
    static private UriContent retrieve( final URI uri )
    {
        if( _URI_CONTENT_CACHE.has( uri ) == true )
        {   // was cached, though may be stale
            return _URI_CONTENT_CACHE.get( uri );
        }

        UriContent uriContent;
        try
        {
            uriContent = readContent( uri, _CONNECTION_TIME_OUT_MILLISEC );
        }
        catch( IOException ex )
        {   // treat as 404
            uriContent = new UriContent( uri );
        }

        _URI_CONTENT_CACHE.put( uriContent );
        return uriContent;
    }

    /**
     * Get the content of an URI as bytes during this Thread without caching.
     *
     * @param timeoutMillisec
     * @param uri
     * @return the content bytes @ URI
     * @throws IOException if FileNotFoundException or server reset connection
     */
    static private UriContent readContent( final URI uri, final int timeoutMillisec ) throws IOException
    {
        final URL youAreEl = uri.toURL(); // *BLOCKS* for DNS resolution
        final URLConnection connection = youAreEl.openConnection();
        connection.setConnectTimeout( timeoutMillisec ); // web servers get busy too

        // required to avoid err 403
        connection.addRequestProperty( "User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)" );
        connection.connect();

        final int size = connection.getContentLength(); // *BLOCKS* connects to remote sever
        if( size > 0 )
        {
            final InputStream is = connection.getInputStream();
            final DataInputStream dis = new DataInputStream( is );

if(false) //... lame, need to recognize encoding and MIME types and add to URIContent
{
            System.out.println( "Header @ "+ uri.toString() );
            final Map<String,List<String>> map = connection.getHeaderFields();
            for( final Map.Entry<String,List<String>> entry : map.entrySet() )
            {
                System.out.println( "field="+ entry.getKey() );
                for( final String string : entry.getValue() )
                {
                    System.out.println( "    ->"+ string );
                }
            }
}

            final byte[] bytes = new byte[ size ];
            dis.readFully( bytes ); // *BLOCKS* to GET from remote server

            dis.close();
            is.close();

            return new UriContent( uri, bytes );
        }
        else
        {
            return new UriContent( uri ); // nothing to read
        }
    }

    static public String getDomain( String urlString )
    {
        int occurrence = 0;
        for( int i = 0; i < urlString.length(); i++ )
        {   // faster than PatternCompile and .split() and StringTokenizer
            if( urlString.charAt( i ) == '/' )
            {
                occurrence++;
                if( occurrence == 3 )
                {
                    return urlString.substring( 0, i );
                }
            }
        }
        return urlString;
    }


    /**
     *
     * @param uriString of web page
     * @return 'true' if Web Browser was informed of request.
     */
    static public boolean browseTo( final String uriString )
    {
        if( uriString.isEmpty() == true ) return true;

        if( Desktop.isDesktopSupported() == true )
        {
            try
            {
                final URI uri = URI.create( uriString ); // may throw IllegalArgumentException
                Desktop.getDesktop().browse( uri ); // THROWS IoException
                return true;
            }
            catch( IOException ex )
            {}
        }

        return false;
    }

//    static public void main( String[] args )
//    {
////            readAvatar( "http://www.kde.org", new Targetable<Image>() { HAS ERR with .ICO processing
//            readLogo( "http://www.gnu.org", new Targetable<Image>() {
//
//                @Override
//                public void target( Image obj )
//                {
//System.out.println( obj.toString() );
//                }
//            } );
//    }
//


}
