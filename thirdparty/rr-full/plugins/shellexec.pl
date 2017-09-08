#-----------------------------------------------------------
# shellexec
# Get ShellExecuteHooks values from Software hive (based on BHO
#   code)
#  
# ShellExecuteHooks are DLLs that load as part of the Explorer.exe process,
#   and can intercept commands.  There are some legitimate applications that
#   run as ShellExecuteHooks, but many times, malware (spy-, ad-ware) will 
#   install here.  ShellExecuteHooks allow you to type a URL into the Start->Run
#   box and have that URL opened in your browser.  For example, in 2001, Michael
#   Dunn wrote KBLaunch, a ShellExecuteHook that looked for "?q" in the Run box
#   and would open the appropriate MS KB article.
#
# Refs:
#   http://support.microsoft.com/kb/914922
#   http://support.microsoft.com/kb/170918
#   http://support.microsoft.com/kb/943460
#
# History:
#   20130410 - added Wow6432Node support
#   20081229 - initial creation
#
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shellexec;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130410);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets ShellExecuteHooks from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %bhos;
	::logMsg("Launching shellexec v.".$VERSION);
	::rptMsg("shellexec v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellExecuteHooks",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellExecuteHooks");
	             
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar (@vals) > 0) {
				foreach my $s (@vals) {
					my $name = $s->get_name();
					next if ($name =~ m/^-/ || $name eq "");
					my $clsid_path = "Classes\\CLSID\\".$name;
					my $clsid;
					if ($clsid = $root_key->get_subkey($clsid_path)) {
						my $class;
						my $mod;
						my $lastwrite;
					
						eval {
							$class = $clsid->get_value("")->get_data();
							$bhos{$name}{class} = $class;
						};
						if ($@) {
							::logMsg("  Error getting Class name for CLSID\\".$name);
							::logMsg("  ".$@);
						}
						eval {
							$mod = $clsid->get_subkey("InProcServer32")->get_value("")->get_data();
							$bhos{$name}{module} = $mod;
						};
						if ($@) {
							::logMsg("  Error getting Module name for CLSID\\".$name);
							::logMsg("  ".$@);
						}
						eval{
							$lastwrite = $clsid->get_subkey("InProcServer32")->get_timestamp();
							$bhos{$name}{lastwrite} = $lastwrite;
						};
						if ($@) {
							::logMsg("  Error getting LastWrite time for CLSID\\".$name);
							::logMsg("  ".$@);
						}
					
						foreach my $b (keys %bhos) {
							::rptMsg($b);
							::rptMsg("  Class     => ".$bhos{$b}{class});
							::rptMsg("  Module    => ".$bhos{$b}{module});
							::rptMsg("  LastWrite => ".gmtime($bhos{$b}{lastwrite}));
							::rptMsg("");
						}
					}
					else {
						::rptMsg($clsid_path." not found.");
						::rptMsg("");
					}
				}
			}
			else {
				::rptMsg($key_path." has no values.  No ShellExecuteHooks installed.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;