#-----------------------------------------------------------
# compname.pl
# Plugin for Registry Ripper; Access System hive file to get the
# computername
# 
# Change history
#   20090727 - added Hostname
#
# References
#   http://support.microsoft.com/kb/314053/
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package autopsycompname;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090727);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets ComputerName and Hostname values from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	#::logMsg("Launching compname v.".$VERSION);
	#::rptMsg("compname v.".$VERSION); # banner
    #::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
		::rptMsg("<CompName>");
		::rptMsg("<mtime></mtime>");
		::rptMsg("<artifacts>");
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $cn_path = $ccs."\\Control\\ComputerName\\ComputerName";
		my $cn;
		if ($cn = $root_key->get_subkey($cn_path)) {
			my $name = $cn->get_value("ComputerName")->get_data();
			#::rptMsg("ComputerName    = ".$name);
			::rptMsg("<compname name=\"ComputerName\">". $name ."</compname>");
		}
		else {
			#::rptMsg($cn_path." not found.");
			#::logMsg($cn_path." not found.");
		}
	}
	else {
		#::rptMsg($key_path." not found.");
		#::logMsg($key_path." not found.");
	}
	
	my $hostname;
	eval {
		my $host_path = $ccs."\\Services\\Tcpip\\Parameters";
		$hostname = $root_key->get_subkey($host_path)->get_value("Hostname")->get_data();
		#::rptMsg("TCP/IP Hostname = ".$hostname);


	};

	my $domain;
	eval {
	  my $domain_path = $ccs."\\Services\\Tcpip\\Parameters";
	  $domain = $root_key->get_subkey($domain_path)->get_value("Domain")->get_data();
	};
	if ($@) {
	  #			::rptMsg("Domain value not found.");
	}
	else {
	  ::rptMsg("<compname name=\"Domain\">". $domain ."</compname>");
	}

	::rptMsg("</artifacts></CompName>");
	
}

1;
