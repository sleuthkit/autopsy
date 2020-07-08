#-----------------------------------------------------------
# runmru.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# RunMru values 
#
# Change history
#
#
# References
#
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package arunmru;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's RunMRU key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	#::logMsg("autospyrunmru");
    if (defined(Parse::Win32Registry->new($ntuser))) {
        my $reg = Parse::Win32Registry->new($ntuser);
        my $root_key = $reg->get_root_key;

        my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RunMRU';
        my $key;
        if ($key = $root_key->get_subkey($key_path)) {
            #::rptMsg("RunMru");
            #::rptMsg($key_path);
            
            my @vals = $key->get_list_of_values();
            ::rptMsg("<runMRU>");
            ::rptMsg("<mtime>".gmtime($key->get_timestamp())."</mtime>");
            ::rptMsg("<artifacts>");
            my %runvals;
            my $mru;
            if (scalar(@vals) > 0) {
                foreach my $v (@vals) {
                    $runvals{$v->get_name()} = $v->get_data() unless ($v->get_name() =~ m/^MRUList/i);
                    $mru = $v->get_data() if ($v->get_name() =~ m/^MRUList/i);
                }
                ::rptMsg("<MRUList>".$mru."</MRUList>");
                foreach my $r (sort keys %runvals) {
                    ::rptMsg("<MRU>".$r."   ".$runvals{$r}."</MRU>");
                }
            }
            else {
                #::rptMsg($key_path." has no values.");
                #::logMsg($key_path." has no values.");
            }
            ::rptMsg("</artifacts>");
            ::rptMsg("</runMRU>");
        }
        else {
            #::rptMsg($key_path." not found.");
            #::logMsg($key_path." not found.");
        }
    }        
}

1;
