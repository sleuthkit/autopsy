#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# logonusername.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# "Logon User Name" value 
#
# Change history
#
#
#
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package autopsylogin;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's Logon User Name value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	#::logMsg("||logonusername||");
    if (defined(Parse::Win32Registry->new($ntuser))) {
        my $reg = Parse::Win32Registry->new($ntuser);
        my $root_key = $reg->get_root_key;
        
        my $logon_name = "Username";
        
        my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer';
        my $key;
        if ($key = $root_key->get_subkey($key_path)) {
            my @vals = $key->get_list_of_values();
            if (scalar(@vals) > 0) {
                #::rptMsg("Logon User Name");
                #::rptMsg($key_path);
                ::rptMsg("<logon>");
                ::rptMsg("<mtime>".gmtime($key->get_timestamp())."</mtime><artifacts>");
                foreach my $v (@vals) {
                    if ($v->get_name() eq $logon_name) {
                        ::rptMsg("<user name=\"".$logon_name."\"> ".$v->get_data() ."</user>");
                    }
                }
                ::rptMsg("</artifacts></logon>");
            }
            else {
                #::rptMsg($key_path." has no values.");
                #::logMsg($key_path." has no values.");
            }
        }
        else {
            #::rptMsg($key_path." not found.");
            #::logMsg($key_path." not found.");
        }
    }
}

1;
