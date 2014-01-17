#-----------------------------------------------------------
# winlivemail.pl
#   Get and display the contents of the key
#   "HKCU\Software\Microsoft\Windows Live Mail"
#
# Change history
#   20111115 [fpi] % created
#   20111118 [fpi] % minor fix
#
# References
#
# copyright 2011 F. Picasso, francesco.picasso@gmail.com
#-----------------------------------------------------------
package winlivemail;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20111118);

sub getConfig{return %config}
sub getShortDescr {
	return "Get & display the contents of the Windows Live Mail key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my @VALOUT = ( 
    [ "Store Root",              undef, undef ],
    [ "Attachment Path",         undef, undef ],
    [ "Default User",            undef, undef ],
    [ "Default Mail Account",    undef, undef ],
    [ "Default LDAP Account",    undef, undef ],
    [ "TotalUpTime",             undef, undef ],
    [ "AppRuns",                 undef, undef ],
    [ "LastRun",                 undef, \&trFILETIME ],
    [ "First Run Done",          undef, undef ],
    [ "Email Security Zone",     undef, undef ],
    [ "DesktopSearchIndexed",    undef, undef ],
    [ "DatabaseVersion",         undef, undef ]
);

my @VALOUT_MAIL = (
    [ "Accounts Checked",           undef, undef ],
    [ "Attach VCard",               undef, undef ],
    [ "Block External Content",     undef, undef ],
    [ "Check Mail on Startup",      undef, undef ],
    [ "Digitally Sign Messages",    undef, undef ],
    [ "EnablePhishing",             undef, undef ],
    [ "Encrypt Messages",           undef, undef ],
    [ "Safe Attachments",           undef, undef ],
    [ "Secure Safe Attachments",    undef, undef ],
    [ "Show Header Info",           undef, undef ],
    [ "Show Images From Contacts",  undef, undef ],
    [ "Warn on Mapi Send",          undef, undef ]
);

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winlivemail v.".$VERSION);
    ::rptMsg("winlivemail v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows Live Mail";
	my $key;
    my $ref;

	if ( $key = $root_key->get_subkey( $key_path ) ) {
		::rptMsg( $key_path );
		::rptMsg( "LastWrite Time ".gmtime($key->get_timestamp())." (UTC)" );
		::rptMsg( "" );
		
        foreach $ref ( @VALOUT ) {
			$ref->[1] = getValueData( $key, $ref->[0], $ref->[2] );
            ::rptMsg( sprintf( "%-20s = %s", $ref->[0], $ref->[1] ) );
		}
        
        my $tab = "  ";
        $key_path .= "\\mail";
        if ( $key = $root_key->get_subkey( $key_path ) ) {
            ::rptMsg("");
            ::rptMsg( $tab.$key_path );
            ::rptMsg( $tab."LastWrite Time ".gmtime($key->get_timestamp())." (UTC)" );
            ::rptMsg( "" );
            
            foreach $ref ( @VALOUT_MAIL ) {
                $ref->[1] = getValueData( $key, $ref->[0], $ref->[2] );
                ::rptMsg( $tab.sprintf( "%-25s = %s", $ref->[0], $ref->[1] ) );
            }
        }
        else {
            ::rptMsg( $key_path." not found." );
            ::logMsg( $key_path." not found." );
        }
	}
	else {
		::rptMsg( $key_path." not found." );
		::logMsg( $key_path." not found." );
	}
}

sub trFILETIME
{
    my $data = shift;
    my ( $t0, $t1 ) = unpack( "VV",$data );
	$data = gmtime( ::getTime( $t0, $t1 ) )." UTC";
    return $data;
}

sub getValueData
{
    my $key = shift; my $vn = shift; my $trans = shift;
    my $vd;
    my $vo = $key->get_value( $vn );
    if ( not defined $vo ) {
        $vd = "<value not found>";
    }
    else {
        $vd = $vo->get_data();
        if ( defined $trans ) {
            $vd = $trans->( $vd );
        }
    }
    return $vd;
}
1;