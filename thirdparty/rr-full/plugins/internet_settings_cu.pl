#------------------------------------------------------------------------------
# internet_settings_cu.pl
#   NTUSER.DAT Internet Settings key parser
#   Note: it's not tested against all IE versions available, neither
#         it parses all available keys/subkeys
#
# Change history
#   20120513 [fpi] % created and working on
#   20120515 [fpi] % first release
#   20120528 [fpi] % released to public
#
# References
#   "Internet Explorer 6.0 Registry Settings"
#       http://msdn.microsoft.com/en-us/library/ms902093.aspx
#   "WinInet Registry Settings"
#       http://msdn.microsoft.com/en-us/library/aa918417.aspx
#
# copyright 2012 F. Picasso francesco.picasso@gmail.com
#------------------------------------------------------------------------------
package internet_settings_cu;
use strict;

my %config = (hive => "NTUSER\.DAT",
              osmask => 22,
              hasShortDescr => 1,
              hasDescr => 0,
              hasRefs => 0,
              version => 20120528);
              
sub getConfig{return %config}
sub getShortDescr {
    return "Get HKCU information on Internet Settings";
}
sub getDescr{}
sub getRefs {
    my %refs = ("Internet Explorer 6.0 Registry Settings" => 
                    "http://msdn.microsoft.com/en-us/library/ms902093.aspx",
                "WinInet Registry Settings" =>
                    "http://msdn.microsoft.com/en-us/library/aa918417.aspx"
    );
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
my $align30 = "%-30s";
my $align40 = "%-40s";

#------------------------------------------------------------------------------

my %PARSED_SUBKEYS = (
    "5.0"                       =>  \&cb50,
    "CACHE"                     =>  \&cbCACHE,
    "P3P"                       =>  \&cbP3P,
    "Url History"               =>  \&cbUrlHistory,
    "Wpad"                      =>  \&cbWpad,
    "ZoneMap"                   =>  \&cbZoneMap
);

my %INTERNET_SETTINGS = (
    "AutoConfigProxy"                       =>  undef,
    "BackgroundConnections"                 =>  \&trBool,
    "CertificateRevocation"                 =>  \&trBool,
    "CoInternetCombineIUriCacheSize"        =>  \&trNumHex,
    "CreateUriCacheSize"                    =>  \&trNumHex,
    "DisableCachingOfSSLPages"              =>  \&trBool,
    "EmailName"                             =>  undef,
    "EnableAutodial"                        =>  \&trBool,
    "EnableHttp1_1"                         =>  \&trBool,
    "EnableNegotiate"                       =>  \&trBool,
    "EnablePunycode"                        =>  \&trBool,
    "GlobalUserOffline"                     =>  \&trBool,
    "IE5_UA_Backup_Flag"                    =>  undef,
    "MigrateProxy"                          =>  \&trBool,
    "MimeExclusionListForCache"             =>  undef,
    "NoNetAutodial"                         =>  \&trBool,
    "PrivacyAdvanced"                       =>  \&trBool,
    "PrivDiscUiShown"                       =>  \&trBool,
    "ProxyEnable"                           =>  \&trBool,
    "ProxyHttp1.1"                          =>  \&trBool,
    "ProxyOverride"                         =>  undef,
    "SecureProtocols"                       =>  \&trNumHex,
    "SecurityIdIUriCacheSize"               =>  \&trNumHex,
    "ShowPunycode"                          =>  \&trBool,
    "SpecialFoldersCacheSize"               =>  \&trNumHex,
    "SyncMode5"                             =>  \&trSyncMode5,
    "UrlEncoding"                           =>  \&trBool,
    "User Agent"                            =>  undef,
    "UseSchannelDirectly"                   =>  \&trHex,
    "WarnOnIntranet"                        =>  \&trBool,
    "WarnOnPost"                            =>  \&trHex,
    "WarnonZoneCrossing"                    =>  \&trBool,
    "ZonesSecurityUpgrade"                  =>  \&trFileTime
);

my %CACHE_VALUES = (
    "LastScavenge"                          =>  \&trBool,
    "LastScavenge_TIMESTAMP"                =>  \&trFileTime,
    "Persisten"                             =>  \&trBool
);

my %WPAD_VALUES = (
    "WpadDecision"                          =>  undef,
    "WpadDecisionReason"                    =>  undef,
    "WpadDecisionTime"                      =>  \&trFileTime,
    "WpadNetworkName"                       =>  undef
);

#------------------------------------------------------------------------------

sub pluginmain {
    my $class = shift;
    my $hive = shift;
    ::logMsg( "Launching internet_settings_cu v.".$VERSION );
    ::rptMsg( "internet_settings_cu v.".$VERSION );
    ::rptMsg( "(".getHive().") ".getShortDescr()."\n" );
    
    my $reg = Parse::Win32Registry->new( $hive );
    my $root_key = $reg->get_root_key;
    my $key_path_main = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    my $key_path = $key_path_main;
    my $key;
    my $tab; my $align;
    my $vdata; my $vname;
    my @subkeys; my $subkey; my @subkeysnp;
    my $callback;

    # ---------------------------------------------------------------
    # 20120513 [fpi] : getting the main key
    $key = $root_key->get_subkey( $key_path );
    if ( not $key ) {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
        return;
    }
    
    # ---------------------------------------------------------------
    # 20120513 [fpi] : parsing all values inside the main key
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $key, \%INTERNET_SETTINGS, $tab2, $align30 );
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg();
    
    # ---------------------------------------------------------------
    # 20120513 [fpi] : getting all the first level subkeys, parsing some of them
    #                  and reporting all subkeys parsed and not parsed as list
	@subkeys = sort {lc $a->get_name() cmp lc $b->get_name} $key->get_list_of_subkeys();
    foreach my $subkey ( @subkeys ) {
        $callback = $PARSED_SUBKEYS{ $subkey->get_name() };
        if ( defined $callback ) {
            ::rptMsg();
            $key_path = $key_path_main."\\".$subkey->get_name();
            ::rptMsg( ' *'.$key_path );
            ::rptMsg( $tab2."LastWrite Time ".gmtime( $subkey->get_timestamp() )." (UTC)" );
            $callback->( $key_path, $subkey, $tab2, $align25 );
        }
        else {
            push @subkeysnp, $subkey;
        }
    }
    
    ::rptMsg( "\nSubkeys not parsed in '$key_path_main'\n" );
	foreach my $subkey ( @subkeysnp ) {
        ::rptMsg( sprintf( $tab4."$align20 ---  %s",
            $subkey->get_name() ) . gmtime( $subkey->get_timestamp() ) . " UTC" );
	}
    ::rptMsg( "" ); 
}

#------------------------------------------------------------------------------

sub trBool
{
    my $data = shift; my $temp = "true ";
    if ( $data != 0 and $data != 1 ) {
        $temp = "$data (WARNING: expected a boolean '0|1'!)";
        return $temp;
    }
    $temp = "false" if ( $data == 0 );
    $temp .= " [$data]";
    return $temp;
}

sub trFileTime
{
    my $data = shift;
    my ( $t0, $t1 ) = unpack( "VV",$data );
	$data = gmtime( ::getTime( $t0, $t1 ) )." UTC";
    return $data;
}

sub trHex
{
    my $data = shift;
    $data = unpack( "H*", $data );
    return "0x".$data;
}

sub trNumHex
{
    my $data = shift;
    return sprintf( "%u [0x%08X]", $data, $data );
}

sub trSkip
{
    return "<skipped>";
}

sub trSyncMode5
{
    my $data = shift; my $ret;
    $ret = sprintf( "%u ", $data );
    if ( $data == 4 ) { $ret .= "(automatically check for updated Web pages)"; }
    elsif ( $data == 3 ) { $ret .= "(always check for updated Web pages)"; }
    elsif ( $data == 2 ) { $ret .= "(check one per session for updated Web pages)"; }
    elsif ( $data == 0 ) { $ret .= "(never check for updated Web pages, use cached pages)"; }
    else { $ret .= "(unknown value)"; }
    return $ret;
}

#------------------------------------------------------------------------------

sub getKeyValues {
    my $key = shift;
    my %vals;
    my @vk = $key->get_list_of_values();
    if (scalar(@vk) > 0) {
        foreach my $v (@vk) {
            next if ($v->get_name() eq "" && $v->get_data() eq "");
            $vals{$v->get_name()} = $v->get_data();
        }
    }
    else {
    }
    return %vals;
}

#------------------------------------------------------------------------------

sub getValueData
{
    # key, value name, translator, use stub
    my $key = shift; my $vn = shift;
    my $trans = shift; my $stub = shift;
    my $vd; my $vo;
    $vo = $key->get_value( $vn );
    if ( not defined $vo ) {
        return undef unless defined $stub;
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

sub rptAllSubKeys
{
    # key, tab, align
	my @subkeys = $_[0]->get_list_of_subkeys();
	foreach my $k (@subkeys) {
        ::rptMsg( sprintf( $_[1]."$_[2] ---  %s",
            $k->get_name() ) . gmtime( $k->get_timestamp() ) . " UTC" );
	}
}

#------------------------------------------------------------------------------

sub rptAllKeyValues
{
    # key, tab, align
    my @vals = sort {lc $a->get_name() cmp lc $b->get_name} $_[0]->get_list_of_values();
	foreach my $v (@vals) {
		my $val = $v->get_name();
		my $data = $v->get_data();
        $val = '(default)' if ( $val eq "" );
        ::rptMsg( sprintf( $_[1]."$_[2] = %s", $val, $data ) );
	}
}
#------------------------------------------------------------------------------

sub rptAllKeyValuesTrans
{
    # key, ttlb, tab, align, 
    my $key = shift; my $ttlb = shift;
    my $tab = shift; my $align = shift;
    my $vname; my $vdata; my $trans;
        
    my @vals = sort {lc $a->get_name() cmp lc $b->get_name} $key->get_list_of_values();
    foreach my $v (@vals) {
        $vname = $v->get_name();
        $vname = '(default)' if ( $vname eq "" );
        $vdata = $v->get_data();
        $trans = ${$ttlb}{$vname};
        $vdata = $trans->( $vdata ) if ( defined $trans );
        ::rptMsg( sprintf( $tab."$align = %s", $vname, $vdata ) );
    }
}

#------------------------------------------------------------------------------

sub cbZoneMap
{
    my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
    my @NETID; my @MACS; my @subkeys; my $subkey;
    
    rptAllKeyValues( $rkey, $tab.$tab2, $align );
   
    ::rptMsg( $tab.$tab2."-- 'ZoneMap' subkeys -- not parsed:" );
	foreach my $subkey ( $rkey->get_list_of_subkeys() ) {
        ::rptMsg( sprintf( $tab.$tab4."$align25 %s",
            $subkey->get_name() ) . gmtime( $subkey->get_timestamp() ) . " UTC" );
	}    
}

#------------------------------------------------------------------------------

sub rptAllSubKeysWpad
{
    # key, tab, align
    my @subkeys = $_[0]->get_list_of_subkeys();
    if ( not scalar( @subkeys ) ) {
        ::rptMsg( sprintf( $_[1]."$_[2] %s", "-- MAC SUBKEYS --", "*no* MAC subkeys (unidentified network)" ) );
        return;
    }  
    ::rptMsg( sprintf( $_[1]."$_[2] %s", "-- MAC SUBKEYS --", "" ) );
	foreach my $k (@subkeys) {
        ::rptMsg( sprintf( $_[1]."$_[2] LastWritten %s",
            $k->get_name() ) . gmtime( $k->get_timestamp() ) . " UTC" );
	}
}

sub cbWpad
{
    my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
    my @NETID; my @MACS; my @subkeys; my $subkey;
    
    # 20120515 [fpi] : divide ID from MACs (brutally rustic raw algo... TBR)
    @subkeys = $rkey->get_list_of_subkeys();
    foreach $subkey ( @subkeys ) {
        my $kname = $subkey->get_name();
        if ( ( substr( $kname, 0, 1 ) eq '{' ) and ( substr( $kname, -1, 1 ) eq '}' ) ) {
            push @NETID, $subkey;
        }
        elsif ( length $kname == 17 ) {
            push @MACS, $subkey;
        }
        else {
            ::logMsg( "Unexpected key '$kname' in $rkeypath" );
        }
    }
    $tab .= $tab2;

    @NETID = sort {$b->get_timestamp >= $a->get_timestamp} @NETID;
    foreach my $subkey ( @NETID ) {
        ::rptMsg();
        ::rptMsg( $tab."NETWORK SUBKEY: ".$subkey->get_name() );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $subkey, \%WPAD_VALUES, $tab.$tab2, $align );
        rptAllSubKeysWpad( $subkey, $tab.$tab2, $align );
    }
    
    @MACS = sort {$a->get_timestamp >= $b->get_timestamp} @MACS;
    foreach my $subkey ( @MACS ) {
        ::rptMsg();
        ::rptMsg( $tab."MACs SUBKEY: ".$subkey->get_name() );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $subkey, \%WPAD_VALUES, $tab.$tab2, $align );
    }
    ::rptMsg();
}

#------------------------------------------------------------------------------

sub cbUrlHistory
{
    my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
    
    rptAllKeyValues( $rkey, $tab.$tab2, $align );
    ::rptMsg();
}

#------------------------------------------------------------------------------

sub cbP3P
{
    my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
    my $key; my @subkeys; my $subkey; my $lkeypath;
   
    if ( $key = $rkey->get_subkey( "History" ) )
    {
        ::rptMsg();
        $lkeypath = $rkeypath."\\History";
        ::rptMsg( $tab.$lkeypath );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
    
        @subkeys = $key->get_list_of_subkeys();
        ::rptMsg( $tab."ANALYST NOTE:" );
        if ( scalar( @subkeys ) > 0 ) {
            ::rptMsg( $tab.$tab2.sprintf( "There are ".
                "%u per-domain cookie decisions subkeys, check them", scalar( @subkeys ) ) );
        }
        else {
            ::rptMsg( $tab.$tab2."No per-domain cookie decisions subkeys are present" );
        }
    }
    else {
        ::rptMsg( $tab.$lkeypath." not present" );
        ::logMsg( $lkeypath." not present" );
    }
    ::rptMsg();
}

#------------------------------------------------------------------------------

sub cbCACHE
{
   my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
   rptAllKeyValuesTrans( $rkey, \%CACHE_VALUES, $tab.$tab2, $align );
   ::rptMsg();
}

#------------------------------------------------------------------------------

sub parseCacheKeyValues
{
    my $key = shift; my $tab = shift; my $align = shift;
    my $vname; my $vdata;

    my @vals = sort {lc $a->get_name() cmp lc $b->get_name} $key->get_list_of_values();

    foreach my $v (@vals) {
        $vname = $v->get_name();
        $vdata = $v->get_data();
        if ( $vname eq "CacheLimit" ) {
            ::rptMsg( sprintf( $tab."$align = %u KB", $vname, $vdata ) );
        }
        elsif ( $vname eq "CacheOptions" ) {
            ::rptMsg( sprintf( $tab."$align = 0x%X", $vname, $vdata ) );
        }
        elsif ( $vname eq "CacheRepair" ) {
            ::rptMsg( sprintf( $tab."$align = 0x%X", $vname, $vdata ) );
        }
        else {
            ::rptMsg( sprintf( $tab."$align = %s", $vname, $vdata ) );
        }
    }
}

sub parseCacheKeys
{
    my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
    my $subkeyname = shift;
    my $key; my $lkeypath;
    my @subkeys; my $subkey;
    
    if ( $key = $rkey->get_subkey( $subkeyname ) ) {
        ::rptMsg();
        ::rptMsg( $tab.$rkeypath."\\".$subkeyname );
        ::rptMsg( $tab."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValues( $key, $tab.$tab2, $align );
        ::rptMsg();
        
        $lkeypath = $rkeypath."\\".$subkeyname;
        @subkeys = sort {lc $a->get_name() cmp lc $b->get_name} $key->get_list_of_subkeys();
        foreach $subkey ( @subkeys ) {
            if ( $subkey->get_name() ne "Extensible Cache" ) {
                ::rptMsg( $tab.$lkeypath."\\".$subkey->get_name() );
                ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() )." (UTC)" );
                parseCacheKeyValues( $subkey, $tab.$tab2, $align );
                ::rptMsg();
            }
        }
        
        if ( $key = $key->get_subkey( "Extensible Cache" ) ) {
            ::rptMsg();
            $lkeypath .= "\\Extensible Cache";
            ::rptMsg( $tab.$lkeypath );
            ::rptMsg( $tab."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
            ::rptMsg();
        
            @subkeys = sort {lc $a->get_name() cmp lc $b->get_name} $key->get_list_of_subkeys();
            foreach $subkey ( @subkeys ) {
                ::rptMsg( $tab.$lkeypath."\\".$subkey->get_name() );
                ::rptMsg( $tab."LastWrite Time ".gmtime( $subkey->get_timestamp() )." (UTC)" );
                parseCacheKeyValues( $subkey, $tab.$tab2, $align );
                ::rptMsg();
            }
        }
        else { ::rptMsg( $tab."subkey 'Extensible Cache' not present" ); ::rptMsg(); }
    }
    else {
        ::rptMsg( $tab.$rkeypath."\\".$subkeyname." not found." );
        ::rptMsg();
        ::logMsg( $rkeypath."\\".$subkeyname." not found." );
    }
}

sub cb50
{
   my $rkeypath = shift; my $rkey = shift; my $tab = shift; my $align = shift;
   
   parseCacheKeys( $rkeypath, $rkey, $tab, $align, "Cache" );
   parseCacheKeys( $rkeypath, $rkey, $tab, $align, "LowCache" );
   
   # NSCookieUpgrade and User Agent keys not parsed (TBR)
}

#------------------------------------------------------------------------------
1;