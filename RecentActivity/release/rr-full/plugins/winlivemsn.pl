#-----------------------------------------------------------
# winlivemsn.pl
#   Microsoft Messenger parser (HKCU)
#   Microsoft Windows Live Messenger parser (HKCU)
#
# Change history
#   20110511 [fpi] % created
#   20110830 [fpi] + banner, no change to the version number
#   20111117 [fpi] * rewritten with new name "winlivemsn"
#   20111118 [fpi] + added parsing of 'SoundEvents'
#
# References
#	Registry Quick Find Chart - AccessData
#	MSN Messenger - Bruce Long Internet Forensics
#   “Forensic artefacts left by Windows Live Messenger 8.0”, Journal of Digital Investigations 2007.v4.i2
#   “The Forensic Recovery of Instant Messages from MSN Messenger and Windows Live Messenger”, Harry Parsonage 08
#   MSN http://imfreedom.org/wiki/MSN
# 
# copyright 2011 F. Picasso <francesco.picasso gmail.com>
#-----------------------------------------------------------
package winlivemsn;
use strict;
use Encode;
use MIME::Base64;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20111118);

sub getConfig{return %config}
sub getShortDescr {
	return "Windows Live Messenger parser";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Registry Quick Find Chart - AccessData" => 
	            "http://www.accessdata.com/media/en_us/print/papers/wp.Registry_Quick_Find_Chart.en_us.pdf",
				"MSN Messenger - Bruce Long Internet Forensics" =>
				"http://www.slidefinder.net/M/MSN_20Messenger/7261350",
				"The Forensic Recovery of Instant Messages from MSN Messenger and Windows Live Messenger" =>
				"http://computerforensics.parsonage.co.uk/downloads/MSNandLiveMessengerArtefactsOfConversations.pdf",
				"Forensic artefacts left by Windows Live Messenger 8.0" =>
				"http://linkinghub.elsevier.com/retrieve/pii/S1742287607000527",
                "MSN protocol reversed" =>
                "http://imfreedom.org/wiki/MSN"
	);
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
my $VERSION = getVersion();

#------------------------------------------------------------------------------

my $tab0 = "";
my $tab2 = "  ";
my $tab4 = "    ";
my $tab6 = "      ";
my $tab8 = "        ";

my $align10 = "%-10s";
my $align15 = "%-15s";
my $align20 = "%-20s";
my $align25 = "%-25s";

#------------------------------------------------------------------------------

my @MSNOBJ_TYPE = (
    "none",
    "Avatar (Unknown, present since Messenger 6.0)",
    "Custom Emoticon",
    "User Tile (Static display picture only)",
    "Shared File (Unkonw, present since Messenger 6.0)",
    "Backgrounds (Static backgrounds only)",
    "History (Unknown)",
    "Deluxe Display Pictures (Dynamic display pictures)",
    "Wink",
    "Map File (A map file contains a list of items in the store)",
    "Dynamic Background (Animated)",
    "Voice Clip",
    "Plug-in State (Saved state of Add-ins)",
    "Roaming Objects (eg.Roaming display picture)",
    "Signature Sounds (Introduced in Messenger 9.0)"
);

#------------------------------------------------------------------------------

my @VALOUT = ( 
    [ "LastAppVersion",     undef, \&trLastAppVersion ],
    [ "AppCompatCanary",    undef, undef ],
    [ "MachineGuid",        undef, undef ],
    [ "MachineName",        undef, \&trUnicodeStr ],
    [ "RtlLogOutput",       undef, undef ]
);

my @VALOUT_SQM = ( 
    [ "TotalUpTime",    undef, undef ]
);

my @VALOUT_PPS = ( 
    [ "DefaultIdentityMigrated",    undef, undef ],
    [ "LiveIdentitiesMigrated",     undef, undef ]
);

my @VALOUT_ACCOUNT = ( 
    [ "MessengerFirstRunDone",      undef, undef ],
    [ "MessageLoggingEnabled",      undef, \&acctMsnLogging ],
    [ "MessageLogPath",             undef, undef ],
    [ "MessageLogVersion",          undef, \&trHex ],
    [ "DateOfLastHighlightLaunch",  undef, \&trFILETIME ],
    [ "LastActiveProvider",         undef, undef ],
    [ "MSN",                        undef, undef ],
    [ "UTL",                        undef, \&acctUTL ],
    [ "UTT",                        undef, \&trUnicodeStr ]
    
);

# ID, LastWriteTime, Email, Logging
my @NOACCOUNT = ( );
my @ACCOUNT = ( );

#------------------------------------------------------------------------------

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg( "Launching winlivemsn v.".$VERSION );
    ::rptMsg("winlivemsn v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner
	
	my $reg = Parse::Win32Registry->new( $ntuser );
	my $root_key = $reg->get_root_key;
	my $kpath = 'Software\\Microsoft\\MSNMessenger';
	my $path = $kpath;
    my $key;
		
	if ( $key = $root_key->get_subkey( $path ) ) {
        rptKeyAndValues( $path, \$key, \@VALOUT, $tab0, $align15 );
        
		$path = $kpath.'\\SQM';
		if ( $key = $root_key->get_subkey( $path ) ) {
            ::rptMsg( "" );
            rptKeyAndValues( $path, \$key, \@VALOUT_SQM, $tab2, $align10 );
		}
		else {
			::rptMsg( $path." not found." );
			::logMsg( $path." not found." );
		}
        
		$path = $kpath.'\\PerPassportSettings';
		if ( $key = $root_key->get_subkey( $path ) ) {
            ::rptMsg( "" );
            rptKeyAndValues( $path, \$key, \@VALOUT_PPS, $tab2, $align10 );
                 
			my @subkeys = $key->get_list_of_subkeys();
			if ( scalar( @subkeys ) > 0 ) {
				foreach my $s (@subkeys) {
                    ::rptMsg( "" );
                    analyzeAccount( \$s, $tab4, $align25 );
				}
			}
			else {
                ::rptMsg( $path." has no subkeys." );
				::logMsg( $path." has no subkeys." );
			}
		}
		else {
			::rptMsg( $path." not found." );
			::logMsg( $path." not found." );
		}
	}
	else {
		::rptMsg( $path." not found." );
		::logMsg( $path." not found." );
	}

    rptAcctSummary();
	::rptMsg( "\n" );

	$kpath = 'Software\\Microsoft\\Windows Live Contacts';
	$path = $kpath;
	
	if ( $key = $root_key->get_subkey( $path ) ) {
		::rptMsg( $path );
	
		::rptMsg( $key->get_name() );
		::rptMsg( "LastWrite Time ".gmtime($key->get_timestamp())." (UTC)" );
		
		::rptMsg( " " );
		$path = $kpath.'\\Database';
		if ( $key = $root_key->get_subkey( $path ) ) {
			::rptMsg( $tab2.$path );
			::rptMsg( $tab2."LastWrite Time ".gmtime($key->get_timestamp())." (UTC)\n" );
			_getAllKeyValue( \$key, $tab2 );
		}
		else {
			::rptMsg( $path." not found." );
			::logMsg( $path." not found." );
		}
        
        ::rptMsg( "" );
		$path = $kpath.'\\Me';
		if ( $key = $root_key->get_subkey( $path ) ) {
			::rptMsg( $tab2.$path );
			::rptMsg( $tab2."LastWrite Time ".gmtime($key->get_timestamp())." (UTC)\n" );
			_getAllKeyValue( \$key, $tab2 );
		}
		else {
			::rptMsg( $path." not found." );
			::logMsg( $path." not found." );
		}
        
        ::rptMsg( "" );
        ::rptMsg( "Analysis Tip: bad accounts should be identified by missing 'shadow' Database\n".
                  "and should not appear under the 'Me' subkey");
	}
	else {
		::rptMsg( $path." not found." );
		::logMsg( $path." not found." );
	}
}

#------------------------------------------------------------------------------

sub trLastAppVersion
{
    my $data = shift;
	my $ver1 = $data >> 24;
    my $ver2 = ( $data >> 16 ) & 0xFF;
    my $ver3 = $data & 0xFFFF;
	return sprintf( "0x%08X (%u.%u.%u)", $data, $ver1, $ver2, $ver3 );
}

sub trUnicodeStr
{
    my $data = shift;
    $data = decode( "UCS-2LE", $data );
    chop( $data ); # remove last NULL (sig)
    return $data;
}

sub trHex
{
    my $data = shift;
    $data = unpack( "H*", $data );
    return "0x".$data;
}

sub trFILETIME
{
    my $data = shift;
    my ( $t0, $t1 ) = unpack( "VV",$data );
	$data = gmtime( ::getTime( $t0, $t1 ) )." UTC";
    return $data;
}

sub acctMsnLogging
{
    my $data = shift; my $acctRef = shift; my $valueObj = shift;
    
    if ( 'REG_BINARY' eq $valueObj->get_type_as_string() ) {
        $data = 'yes (binary not reported)'; ${$acctRef}[3] = 'yes';
    }
    else {
        if ( 0 == $data ) { $data = "no ($data)"; ${$acctRef}[3] = 'no'; }
        else {
            $data = "unknown ($data)"; ${$acctRef}[3] = 'unknown';
            ::logMsg( "expected a value of 0 for REG_DWORD MessageLoggingEnabled but found unknown '$data'" ); }
    }
    return $data;
}

sub acctUTL
{
    my $data = shift; my $acctRef = shift;
        
    if ( $data =~ m/Creator="([^"]*)/) {
        ${$acctRef}[2] = $1;
    }
    else {
        ${$acctRef}[2] = 'unknown';
        ::logMsg( "accUTL method not found email address as expected" );
    }
    return $data;
}

#------------------------------------------------------------------------------

sub getValueData
{
    my $keyRef = shift; my $vn = shift; my $trans = shift;
    my $vd; my $vo;
    $vo = ${$keyRef}->get_value( $vn );
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

#------------------------------------------------------------------------------

sub rptKeyAndValues
{
    my $path = shift; my $keyRef = shift; my $valoutRef = shift;
    my $tab = shift; my $align = shift;
    ::rptMsg( $tab.$path );
	::rptMsg( $tab."LastWrite Time ".gmtime( ${$keyRef}->get_timestamp() )." (UTC)\n" );
    foreach my $ref ( @{$valoutRef} ) {
        $ref->[1] = getValueData( $keyRef, $ref->[0], $ref->[2] );
        ::rptMsg( sprintf( $tab."$align = %s", $ref->[0], $ref->[1] ) );
    }
}

#------------------------------------------------------------------------------

sub analyzeAccount
{
    my $keyRef = shift; my $tab = shift; my $align = shift;
    my $accid = ${$keyRef}->get_name();
    my $acckt = ${$keyRef}->get_timestamp();
    
    ::rptMsg( $tab."Key (account): $accid" );
    ::rptMsg( $tab."LastWrite Time ".gmtime( $acckt ) );
    
    my @values = ${$keyRef}->get_list_of_values();
	if ( scalar( @values ) == 1 ) {
        ::rptMsg( sprintf( $tab."%s = %s", $values[0]->get_name(), $values[0]->get_data() ) );
        push( @NOACCOUNT, [ $accid, $acckt, "n/a", "n/a" ] );
        return;
    }
    
    my @acct = ( $accid, $acckt, undef, undef );
    my $vd; my $vo;
    foreach my $ref ( @VALOUT_ACCOUNT ) {
        $vo = ${$keyRef}->get_value( $ref->[0] );
        if ( not defined $vo ) { $vd = "<value not found>"; }
        else {
            $vd = $vo->get_data();
            if ( defined $ref->[2] ) { $vd = $ref->[2]->( $vd, \@acct, $vo ); }
            # decode msnobj
            if ( "UTL" eq $ref->[0] ) {
                rptDecodedMsnObj( $vd, $tab, $align );
            }
        }
        $ref->[1] = $vd;
        ::rptMsg( sprintf( $tab."$align = %s", $ref->[0], $ref->[1] ) );
    }
    
    ::rptMsg( $tab."---\\" );
    if ( my $subkey = ${$keyRef}->get_subkey( 'DisplayPicsMRUList' ) ) {
        ::rptMsg( $tab."SubKey : DisplayPicsMRUList" );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() ) );
        $vo = $subkey->get_value( 'CurrentEntries' );
        if ( defined $vo ) {
            $vd = int( $vo->get_data() );
            ::rptMsg( sprintf( $tab."$align = %s", 'CurrentEntries', $vd ) );
            if ( $vd > 0 ) {
                my $temp = $vd - 1;
                foreach my $i ( 0..$temp ) {
                    $vo = $subkey->get_value( $i );
                    $vd = $vo->get_data();
                    $vd = decode( "UCS-2LE", $vd );
                    chop( $vd );
                    ::rptMsg( sprintf( $tab."$align = %s", $i, $vd ) );
                    rptDecodedMsnObj( $vd, $tab, $align );
                    # try to get email if not yet (re-using acctUTL)
                    if ( not defined $acct[2] ) {
                        acctUTL( $vd, \@acct );
                    }
                }
            }
        }
        else { ::rptMsg( $tab."No CurrentEntries" ); }
    }
    else {
        ::rptMsg( $tab. "DisplayPicsMRUList subkey not found." );
        ::logMsg( $tab. "DisplayPicsMRUList subkey not found." );
	}
    
    ::rptMsg( $tab."---\\" );
    # 20111118 [fpi] add check for SoundEvents trying to retrieve email address
    if ( my $subkey = ${$keyRef}->get_subkey( 'SoundEvents' ) ) {
        ::rptMsg( $tab."SubKey : SoundEvents" );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() ) );
        
		my @subkeys = $subkey->get_list_of_subkeys();
		if ( scalar( @subkeys ) > 0 ) {
			foreach my $s (@subkeys) {
                $vo = $s->get_value( 'OSName' );
                if ( defined $vo ) {
                    ::rptMsg( $tab."------\\" );
                    ::rptMsg( $tab."SubKey : SoundEvents\\".$s->get_name() );
                    ::rptMsg( $tab."LastWrite Time ".gmtime( $s->get_timestamp() ) );
                    $vd = $vo->get_data();
                    ::rptMsg( sprintf( $tab."$align = %s", 'OSName', $vd ) );
                    rptDecodedMsnObj( $vd, $tab, $align );
                    # try to get email if not yet (re-using acctUTL)
                    if ( not defined $acct[2] ) {
                        acctUTL( $vd, \@acct );
                    }
                }
			}
		}
        else { ::rptMsg( $tab."no subkeys found" ); }
    }
    else {
        ::rptMsg( $tab. "SoundEvents subkey not found." );
        ::logMsg( $tab. "SoundEvents subkey not found." );
	}
    push( @ACCOUNT, [ @acct ] );
}

#------------------------------------------------------------------------------

sub rptDecodedMsnObj
{
    my $data = shift; my $tab = shift; my $align = shift;
    my $temp; my $leg;
    
    if ( $data =~ m/Creator="([^"]*)/ ) {
        $leg = "---> creator account";
        ::rptMsg( sprintf( $tab."$align = %s", $leg, $1 ) );
    }
    else {
        $leg = "unable to get Creator account";
        ::rptMsg( sprintf( $tab."$align", $leg ) );
    }
 
    if ( $data =~ m/Type="([^"]*)/ ) {
        $leg = "---> decoded 'Type=$1'";
        ::rptMsg( sprintf( $tab."$align = %s", $leg, $MSNOBJ_TYPE[$1] ) );
    }
    else {
        $leg = "unable to decode MSNOBJ type";
        ::rptMsg( sprintf( $tab."$align", $leg ) );
    }
    
    if ( $data =~ m/Friendly="([^"]*)/ ) {
        $leg = "---> decoded 'Friendly'";
        $temp = decode_base64( $1 );
        $temp = decode( "UCS-2LE", $temp );
        chop( $temp );
        ::rptMsg( sprintf( $tab."$align = %s", $leg, $temp ) );
    }
    else {
        $leg = "unable to decode MSNOBJ type";
        ::rptMsg( sprintf( $tab."$align", $leg ) );
    }
}

#------------------------------------------------------------------------------

sub _getAllKeyValue() {
	my @vals = ${$_[0]}->get_list_of_values();
	my $tab = $_[1];
	foreach my $v (@vals) {
		my $val = $v->get_name();
		my $data = $v->get_data();
		::rptMsg( $tab.$val." = ".$data );
	}
}

#------------------------------------------------------------------------------

sub rptAcctSummary
{
    ::rptMsg( "\n" );
    ::rptMsg( "ACCOUNT SUMMARY" );
    ::rptMsg( "" );
    if ( scalar( @ACCOUNT ) > 0 ) {
        ::rptMsg( sprintf( " %-10s | %-24s | %-30s | %s", "ID", "IDKey Last Write Time", "Account", "Log") );
        ::rptMsg( "-------------------------------------------------------------------------------");
        foreach my $acct ( sort { $a->[1] <=> $b->[1] } @ACCOUNT) {
            ::rptMsg( sprintf( " %-10s | %-24s | %-30s | %s",
                $acct->[0], "".gmtime( $acct->[1] ), $acct->[2], $acct->[3] ) );
        }
    }
    else { ::rptMsg( "no accounts retrieved" ); }
    
    ::rptMsg( "\n" );
    ::rptMsg( "BAD ACCOUNT SUMMARY" );
    ::rptMsg( "bad login attempts or at least account without any information" );
    ::rptMsg( "" );
    if ( scalar( @NOACCOUNT ) > 0 ) {
        ::rptMsg( sprintf( " %-10s | %-24s | %-30s | %s", "ID", "IDKey Last Write Time", "Account", "Log") );
        ::rptMsg( "-------------------------------------------------------------------------------");
        foreach my $acct ( sort { $a->[1] <=> $b->[1] } @NOACCOUNT) {
            ::rptMsg( sprintf( " %-10s | %-24s | %-30s | %s",
                $acct->[0], "".gmtime( $acct->[1] ), $acct->[2], $acct->[3] ) );
        }
    }
    else { ::rptMsg( "no bad accounts retrieved" ); }
}

#------------------------------------------------------------------------------
1;