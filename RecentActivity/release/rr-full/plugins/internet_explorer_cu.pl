#------------------------------------------------------------------------------
# internet_explorer_cu.pl
#   NTUSER.DAT Internet Explorer key parser
#   Try to get useful information on IE
#   Note: it's not tested against all IE versions available
#   WARNING: there exist a huge work to be done, IE settings
#            are a lot and they are sparse in registries
#
# Change history
#   20120426 [fpi] % created and working on
#   20120513 [fpi] % first release
#   20120528 [fpi] % released to public
#
# References
#   "Geoff Chappell - Internet Explorer Registry API " => 
#       "http://www.geoffchappell.com/studies/windows/ie/iertutil/api/ierapi/index.htm",
#   "Internet Explorer Maintenance Extension Tools and Settings"
#     http://technet.microsoft.com/en-us/library/cc736412%28v=ws.10%29.aspx
#   "Introduction to Web Storage"
#     http://msdn.microsoft.com/en-us/library/cc197062%28v=vs.85%29.aspx
#   "How can I configure my Internet Explorer browser settings after I have removed malicious software from my computer?"
#       http://support.microsoft.com/kb/895339
#   "How to Change the Internet Explorer Window Title"
#       http://support.microsoft.com/kb/176497
#
#   The plugin will not parse *every* IE subkeys. The list of subkeys I was able
#   to found inside my NTUSER.DAT registries (a join of XP, Vista, 7) is following. Note that:
#   (P) means parsed, (*) means not parsed but interesting (a TODO), nothing means not parsed.
#
#   Registries coming from (and tested on):
#       (A) Windows7 Professional 32bit - IE 9.0.8112.16421
#       (B) Windows7 Ultimate 64bit     - IE 9.0.8112.16421
#       (C) Windows XP Home 32bit       - IE 8.0.6001.18702
#       (D) Windows Vista 64bit         - IE 7.0.6002.18005
#
#   HKCU\Software\Microsoft\Internet Explorer subkeys list:
#
#   Activities                  (*)         [ A            ]
#   ApprovedExtensions          (*)         [     B        ]
#   ApproveExtensionsMigration  (*)         [  A  B        ]
#   AutoComplete                (P)         [  A           ]
#   BrowserEmulation                        [  A  B  C     ]
#   CaretBrowsing                           [  A           ]
#   CommandBar                              [  A  B  C  D  ]
#   Default HTML Editor                     [        C  D  ]
#   Default MHTML Editor                    [           D  ]
#   Desktop                                 [  A  B  C  D  ]
#   Document Windows                        [  A  B  C  D  ]
#   DOMStorage                  (P)         [  A  B  C     ]
#   Download                    (*)         [  A  B  C  D  ]
#   DxTrans                                 [  A           ]
#   Expiration                              [  A           ]
#   Explorer Bars                           [  A           ]
#   Extensions                  (*)         [  A  B  C  D  ]
#   Feed Discovery                          [  A           ]
#   Feeds                                   [  A        D  ]
#   Geolocation                 (*)         [  A           ]
#   GPActivities                            [  A           ]
#   GPU                                     [  A  B        ]
#   Help_Menu_URLs                          [  A  B  C  D  ]
#   IEDevTools                  (*)         [  A  B        ]
#   IETld                       (P)         [  A  B  C     ]
#   InformationBar                          [        C  D  ]
#   IntelliForms                (*)         [  A  B  C  D  ]
#   International               (*)         [  A  B  C  D  ]
#   InternetRegistry                        [  A  B  C  D  ]
#   LinksBar                                [  A  B  C     ]
#   LinksExplorer                           [  A     C  D  ]
#   LowRights                               [     B     D  ]
#   LowRegistry                             [  A  B  C  D  ]
#   Main                        (P)         [  A  B  C  D  ]
#   MAO Settings                            [  A  B  C     ]
#   Media                                   [  A     C  D  ]
#   MenuExt                     (*)         [  A  B  C  D  ]
#   MINIE                                   [  A  B        ]
#   New Windows                             [  A  B  C  D  ]
#   PageSetup                               [  A  B  C  D  ]
#   PhishingFilter              (*)         [  A  B  C  D  ]
#   Privacy                     (P)         [  A     C     ] (user settings ndr)
#   ProtocolExecute                         [  A           ]
#   Recovery                    (P)         [  A  B  C     ]
#   Safety                                  [  A           ]
#   SearchScopes                (*)         [  A  B  C  D  ]
#   SearchUrl                               [  A  B  C  D  ]
#   Security                    (*)         [  A  B  C  D  ]
#   Services                                [  A  B  C  D  ] (empty? ndr)
#   Settings                                [  A  B  C  D  ]
#   Setup                                   [  A  B     D  ]
#   SiteMode                                [  A  B  C  D  ]
#   SQM                         (*)         [  A  B  C     ]
#   Styles                                  [  A           ]
#   Suggested Sites             (P)         [  A  B  C     ]
#   TabbedBrowsing                          [  A  B  C  D  ]
#   TaskbarPreview                          [  A           ]
#   Text Scaling                            [  A           ]
#   Toolbar                                 [  A  B  C  D  ]
#   TypedURLs                               [     B  C     ] (hum?! ndr)
#   UpgradeIEAd                             [  A           ]
#   URLSearchHooks              (*)         [  A  B  C  D  ]
#   User Preferences            (*)         [  A  B  C     ]
#   View Source Editor                      [  A           ]
#   Zoom                                    [  A  B  C  D  ]
# 
# copyright 2012 F. Picasso francesco.picasso@gmail.com
#------------------------------------------------------------------------------
package internet_explorer_cu;
use strict;

my %config = (hive => "NTUSER\.DAT",
              osmask => 22,
              hasShortDescr => 1,
              hasDescr => 0,
              hasRefs => 0,
              version => 20120528);
              
sub getConfig{return %config}
sub getShortDescr {
    return "Get HKCU information on Internet Explorer";
}
sub getDescr{}
sub getRefs {
    my %refs = ("Geoff Chappell - Internet Explorer Registry API " => 
                    "http://www.geoffchappell.com/studies/windows/ie/iertutil/api/ierapi/index.htm",
				"Internet Explorer Maintenance Extension Tools and Settings" =>
                    "http://technet.microsoft.com/en-us/library/cc736412%28v=ws.10%29.aspx",
                "Introduction to Web Storage" =>
                    "http://msdn.microsoft.com/en-us/library/cc197062%28v=vs.85%29.aspx",
                "How can I configure my Internet Explorer browser settings after I have removed malicious software from my computer?" =>
                    "http://support.microsoft.com/kb/895339",
                "How to Change the Internet Explorer Window Title" =>
                    "http://support.microsoft.com/kb/176497"
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

my %IE_MAIN_TRANSLATE = (
    "AdminTabProcs"                         =>  \&trBool,
    "AllowWindowReuse"                      =>  \&trBool,
    "AlwaysShowMenus"                       =>  \&trBool,
    "AutoSearch"                            =>  \&trBool,
    "Cleanup HTCs"                          =>  \&trBool,
    "CompatibilityFlags"                    =>  \&trNumHex,
    "Display Inline Videos"                 =>  \&trBool,
    "DNSPreresolution"                      =>  \&trNumHex,
    "Do404Search"                           =>  \&trDo404Search,
    "DOMStorage"                            =>  \&trBool,
    "DownloadWindowPlacement"               =>  \&trSkip,
    "EnableSearchPane"                      =>  \&trBool,
    "ForceGDIPlus"                          =>  \&trBool,
    "FrameMerging"                          =>  \&trBool,
    "FrameShutdownDelay"                    =>  \&trBool,
    "FrameTabWindow"                        =>  \&trBool,
    "GotoIntranetSiteForSingleWordEntry"    =>  \&trBool,
    "HangRecovery"                          =>  \&trBool,
    "HistoryViewType"                       =>  \&trHex,
    "IE8RunOnceCompletionTime"              =>  \&trFileTime,
    "IE8RunOnceLastShown"                   =>  \&trBool,
    "IE8RunOnceLastShown_TIMESTAMP"         =>  \&trFileTime,
    "IE8RunOncePerInstallCompleted"         =>  \&trBool,
    "IE8TourShown"                          =>  \&trBool,
    "IE8TourShownTime"                      =>  \&trFileTime,
    "IE9RecommendedSettingsNo"              =>  \&trBool,
    "IE9RunOnceCompletionTime"              =>  \&trFileTime,
    "IE9RunOnceLastShown"                   =>  \&trBool,
    "IE9RunOncePerInstallCompleted"         =>  \&trBool,
    "IE9TourNoShow"                         =>  \&trBool,
    "IE9TourShown"                          =>  \&trBool,
    "IE9TourShownTime"                      =>  \&trFileTime,
    "MinIEEnabled"                          =>  \&trBool,
    "NoUpdateCheck"                         =>  \&trBool,
    "NscSingleExpand"                       =>  \&trBool,
    "Q300829"                               =>  \&trBool,
    "SearchControlWidth"                    =>  \&trSkip,
    "SessionMerging"                        =>  \&trBool,
    "Show image placeholders"               =>  \&trBool,
    "ShutdownWaitForOnUnload"               =>  \&trBool,
    "SmoothScroll"                          =>  \&trSkip,
    "Start Page Redirect Cache_TIMESTAMP"   =>  \&trFileTime,
    "StatusBarWeb"                          =>  \&trBool,
    "SuppressScriptDebuggerDialog"          =>  \&trBool,
    "TabShutdownDelay"                      =>  \&trNumHex,
    "Use Stylesheets"                       =>  \&trBool,
    "UseHR"                                 =>  \&trBool,
    "UseThemes"                             =>  \&trBool,
    "Window_Placement"                      =>  \&trSkip,
    "XDomainRequest"                        =>  \&trBool,
    "XMLHTTP"                               =>  \&trBool
);

my %IE_MAIN_WINSEARCH_TRANSLATE = (
    "AutoCompleteGroups"                    =>  \&trNumHex,
    "Cleared"                               =>  \&trBool,
    "Cleared_TIMESTAMP"                     =>  \&trFileTime,
    "ConfiguredScopes"                      =>  \&trNumHex,
    "Disabled"                              =>  \&trBool,
    "EnabledScopes"                         =>  \&trNumHex,
    "LastCrawl"                             =>  \&trFileTime,
    "UpgradeTime"                           =>  \&trFileTime
);

my %IE_PRIVACY_TRANSLATE = (
    "CleanDownloadHistory"                  =>  \&trBool,
    "CleanInPrivateBlocking"                =>  \&trBool,
    "CleanPassword"                         =>  \&trBool,
    "CleanTrackingProtection"               =>  \&trBool,
    "ClearBrowsingHistoryOnExit"            =>  \&trBool,
    "UseAllowList"                          =>  \&trBool
);

my %IE_RECOVERY_TRANSLATE = (
    "AutoRecover"                           =>  \&trBool,
    "NoReopenLastSession"                   =>  \&trBool
);

my %IE_SUGGSITES_TRANSLATE = (
    "MigrationTime"                         =>  \&trFileTime,
    "ObjectsCreated"                        =>  \&trBool,
    "ObjectsCreated_TIMESTAMP"              =>  \&trFileTime
);

#------------------------------------------------------------------------------

sub pluginmain {
    my $class = shift;
    my $hive = shift;
    ::logMsg( "Launching internet_explorer_cu v.".$VERSION );
    ::rptMsg( "internet_explorer_cu v.".$VERSION );
    ::rptMsg( "(".getHive().") ".getShortDescr()."\n" );
    
    my $reg = Parse::Win32Registry->new( $hive );
    my $root_key = $reg->get_root_key;
    my $key_path_ie = "Software\\Microsoft\\Internet Explorer";
    my $key_path = $key_path_ie;
    my $key;
    my $tab; my $align;
    my $vdata; my $vname;

    # 20120426 [fpi] : getting the main key
    $key = $root_key->get_subkey( $key_path );
    if ( not $key ) {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
        return;
    }
    
    # 20120426 [fpi] : getting, if available, the DownloadDirectory
    $tab = $tab2;
    $align = $align10;
    $vname = "Download Directory";
    ::rptMsg( $key_path );
    ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
    $vdata = getValueData( $key, $vname, undef );
    ::rptMsg( sprintf( $tab."$align = '%s'", $vname, $vdata ) );
    ::rptMsg( "" );

    # ---------------------------------------------------------------
    # 20120426 [fpi] : not parsing "ApprovedExtensionsMigration" and
    #                  "ApprovedExtensions" subkeys, which could be
    #                  useful for malware removal and/or for IE timestamping
    #                  Ref: "Internet Explorer Maintenance Extension Tools and Settings"
    #                       http://technet.microsoft.com/en-us/library/cc736412%28v=ws.10%29.aspx
    
    # ---------------------------------------------------------------
    # 20120426 [fpi] : parsing, if available, the AutoComplete subkey
    $key_path = $key_path_ie."\\AutoComplete";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValues( $key, $tab2, $align10 );
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );
    
    # ---------------------------------------------------------------
    # 20120426 [fpi] : parsing "DOMstorage", no informations (apart guessing) on the Total
    #                  subkey and values
    #                  Ref: "Introduction to Web Storage"
    #                       http://msdn.microsoft.com/en-us/library/cc197062%28v=vs.85%29.aspx
    $key_path = $key_path_ie."\\DOMStorage";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        ::rptMsg( "Subkeys:" );
        rptAllSubKeys( $key, $tab2, $align20 );
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );

    # ---------------------------------------------------------------
    # 20120502 [fpi] : parsing "IETld", no informations found, guessing
    #                  I sometimes noticed a discrepancy in the last WORD (16bit)
    #                  value between SOFTWARE key and NTUSER key (??)
    $key_path = $key_path_ie."\\IETld";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        $vname = "IETldDllVersionHigh";
        $vdata = getValueData( $key, $vname, undef, 1 );
        my ($vhi1, $vhi2) = ("????", "????");
        if ( defined $vdata ) { $vhi1 = $vdata >> 16; $vhi2 = $vdata & 0x0000FFFF; }
        $vname = "IETldDllVersionLow";
        $vdata = getValueData( $key, $vname, undef, 1 );
        my ($vlo1, $vlo2) = ("????", "????");
        if ( defined $vdata ) { $vlo1 = $vdata >> 16; $vlo2 = $vdata & 0x0000FFFF; }
        ::rptMsg( $tab2."Internet Explorer version = $vhi1.$vhi2.$vlo1.$vlo2" );
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );
    
    # ---------------------------------------------------------------
    # 20120502 [fpi] : parsing "Main" and "WindowsSearch" subkey.
    #                  Not parsing subkeys "FeatureControl" (could be relevant for
    #                  the security settings) and "Touch".
    $key_path = $key_path_ie."\\Main";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $key, \%IE_MAIN_TRANSLATE, $tab2, $align40 );
        #--- Windows Search subkey
        $key_path .= "\\WindowsSearch";
        if ( $key = $root_key->get_subkey( $key_path ) ) {
            ::rptMsg( "" );
            ::rptMsg( $tab2.$key_path );
            ::rptMsg( $tab2."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
            rptAllKeyValuesTrans( $key, \%IE_MAIN_WINSEARCH_TRANSLATE, $tab4, $align25 );
        }
        else {
            ::rptMsg( $tab.$key_path." not found." );
            ::logMsg( $key_path." not found." );
        }
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );
    
    # ---------------------------------------------------------------
    # 20120502 [fpi] : parsing "Privacy", no info here apart guessing. Tests were
    #                  made on Win7 systems: the presence of this key should attest
    #                  that the user changed the Privacy settings; the absence that
    #                  IE is using defaults settings. Counterchecks welcome.
    $key_path = $key_path_ie."\\Privacy";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $key, \%IE_PRIVACY_TRANSLATE, $tab2, $align30 );
    }
    else {
        ::rptMsg( $key_path." not found (IE should use the default Privacy settings)" );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );
    
    # ---------------------------------------------------------------
    # 20120502 [fpi] : parsing "Recovery", no information just parsing
    $key_path = $key_path_ie."\\Recovery";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $key, \%IE_RECOVERY_TRANSLATE, $tab2, $align25 );
        #--- Subkeys
        $key_path = $key_path_ie."\\Recovery"."\\Active";
        if ( $key = $root_key->get_subkey( $key_path ) ) {
            ::rptMsg( "\n".$tab2.$key_path );
            ::rptMsg( $tab2."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
            rptAllKeyValues( $key, $tab4, $align25 );
        }
        else {
            ::rptMsg( "\n".$tab2.$key_path." not found." );
            ::logMsg( $key_path." not found." );
        }
        $key_path = $key_path_ie."\\Recovery"."\\AdminActive";
        if ( $key = $root_key->get_subkey( $key_path ) ) {
            ::rptMsg( "\n".$tab2.$key_path );
            ::rptMsg( $tab2."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
            rptAllKeyValues( $key, $tab4, $align25 );
        }
        else {
            ::rptMsg( "\n".$tab2.$key_path." not found." );
            ::logMsg( $key_path." not found." );
        }
        $key_path = $key_path_ie."\\Recovery"."\\PendingDelete";
        if ( $key = $root_key->get_subkey( $key_path ) ) {
            ::rptMsg( "\n".$tab2.$key_path );
            ::rptMsg( $tab2."LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
            rptAllKeyValues( $key, $tab4, $align25 );
        }
        else {
            ::rptMsg( "\n".$tab2.$key_path." not found." );
            ::logMsg( $key_path." not found." );
        }
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
    }
    ::rptMsg( "" );
    
    # ---------------------------------------------------------------
    # 20120502 [fpi] : parsing "Suggested Site", lot of web info regarding
    #                  the privacy issue derived from this feature. But almost
    #                  every privacy issue is a good source for an analyst ;)
    $key_path = $key_path_ie."\\Suggested Sites";
    if ( $key = $root_key->get_subkey( $key_path ) ) {
        ::rptMsg( $key_path );
        ::rptMsg( "LastWrite Time ".gmtime( $key->get_timestamp() )." (UTC)" );
        rptAllKeyValuesTrans( $key, \%IE_SUGGSITES_TRANSLATE, $tab2, $align30 );
    }
    else {
        ::rptMsg( $key_path." not found." );
        ::logMsg( $key_path." not found." );
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

sub trDo404Search
{
    my $data = shift; my $temp;
    $temp = unpack( "V" , $data );
    return $temp." [0x".unpack( "H*", $data )."]";
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
        $vdata = $v->get_data();
        $trans = ${$ttlb}{$vname};
        $vdata = $trans->( $vdata ) if ( defined $trans );
        ::rptMsg( sprintf( $tab."$align = %s", $vname, $vdata ) );
    }
}

#------------------------------------------------------------------------------
1;