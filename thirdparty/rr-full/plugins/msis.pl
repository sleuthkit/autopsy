#-----------------------------------------------------------
# msis.pl
# Plugin to determine the MSI packages installed on the system
#
# Change history:
#   20090911 - created
#
# References:
#   http://support.microsoft.com/kb/290134
#   http://support.microsoft.com/kb/931401
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package msis;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090911);

sub getConfig{return %config}

sub getShortDescr {
	return "Determine MSI packages installed on the system";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %msi;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching msis v.".$VERSION);
	 ::rptMsg("msis v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Classes\\Installer\\Products";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $lastwrite = $s->get_timestamp();
				
				my $product;
				eval {
					$product = $s->get_value("ProductName")->get_data();
				};
				
				my $path;
				my $pkg;
				
				eval {
					my $p = $s->get_subkey("SourceList")->get_value("LastUsedSource")->get_data();
					$path = (split(/;/,$p,3))[2];
				};
				
				eval {
					$pkg = $s->get_subkey("SourceList")->get_value("PackageName")->get_data();
				};
				
				push(@{$msi{$lastwrite}},$product.";".$path.$pkg);
			}
			
			
			foreach my $t (reverse sort {$a <=> $b} keys %msi) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$msi{$t}}) {
					::rptMsg("  ".$item);
				}
			}
			
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;