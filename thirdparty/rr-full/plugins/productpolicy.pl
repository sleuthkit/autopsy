#-----------------------------------------------------------
# productpolicy.pl
# Extract/parse the ControlSet00x\Control\ProductOptions\ProductPolicy value
# 
# NOTE: For Vista and 2008 ONLY; the value structure changed with Windows 7
# 
# Change History:
#    20091116 - created
#
# Ref: 
#    http://www.geoffchappell.com/viewer.htm?doc=studies/windows/km/ntoskrnl/
#            api/ex/slmem/productpolicy.htm&tx=19
#    http://www.geoffchappell.com/viewer.htm?doc=notes/windows/license/
#            install.htm&tx=3,5,6;4        
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package productpolicy;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091116);

sub getConfig{return %config}

sub getShortDescr {
	return "Parse ProductPolicy value (Vista & Win2008 ONLY)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %prodinfo = (1 => "Ultimate",
                2 => "Home Basic",
                3 => "Home Premium",
                5 => "Home Basic N",
                6 => "Business",
                7 => "Standard",
                8 => "Data Center",
                10 => "Enterprise",
                11 => "Starter",
                12 => "Data Center Core",
                13 => "Standard Core",
                14 => "Enterprise Core",
                15 => "Business N");
	
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	::logMsg("Launching productpolicy v.".$VERSION);
	::rptMsg("productpolicy v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $curr;
	eval {
		$curr = $root_key->get_subkey("Select")->get_value("Current")->get_data();
	};
	$curr = 1 if ($@); 
	
	my $key;
	my $key_path = "ControlSet00".$curr."\\Control\\ProductOptions";
	if ($key = $root_key->get_subkey($key_path)) {
		my $prod;
		eval {
			$prod = $key->get_value("ProductPolicy")->get_data();
		};
		if ($@) {
			::rptMsg("Error getting ProductPolicy value: $@");
		}
		else {	
			my %pol = parseData($prod);	
			::rptMsg("");
			::rptMsg("Note: This plugin applies to Vista and Windows 2008 ONLY.");
			::rptMsg("For a listing of names and values, see:");
			::rptMsg("http://www.geoffchappell.com/viewer.htm?doc=notes/windows/license/install.htm&tx=3,5,6;4");
			::rptMsg("");	
			foreach my $p (sort keys %pol) {
				::rptMsg($p." - ".$pol{$p});
			}
			
			if (exists $prodinfo{$pol{"Kernel\-ProductInfo"}}) {
				::rptMsg("");
				::rptMsg("Kernel\-ProductInfo = ".$prodinfo{$pol{"Kernel\-ProductInfo"}});
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseHeader {
# Ref: http://www.geoffchappell.com/viewer.htm?doc=studies/windows/km/ntoskrnl/
#             api/ex/slmem/productpolicy.htm&tx=19,21
	my %h;
	my @v = unpack("V*",shift);
	$h{size} = $v[0];
	$h{array} = $v[1];
	$h{marker} = $v[2];
	$h{version} = $v[4];
	return %h;
}

sub parseData {
	my $pd = shift;
	my %policy;
	my $h = substr($pd,0,0x14);
	my %hdr = parseHeader($h);
	my $total_size = $hdr{size};
	my $cursor = 0x14;
	
	while ($cursor <= $total_size) {
		my @vals = unpack("v4V2",	substr($pd,$cursor,0x10));	
		my $value = substr($pd,$cursor,$vals[0]);
		my $name = substr($value,0x10,$vals[1]);
		$name =~ s/\x00//g;
		
		my $data = substr($value,0x10 + $vals[1],$vals[3]);
		if ($vals[2] == 4) {
#			$data = sprintf "0x%x",unpack("V",$data);
			$data = unpack("V",$data);
		}
		elsif ($vals[2] == 1) {
			$data =~ s/\x00//g;
		}
		elsif ($vals[2] == 3) {
			$data = unpack("H*",$data);
		}
		else {
			
		} 
		$policy{$name} = $data;
		$cursor += $vals[0];
	}
	delete $policy{""};
	return %policy;
}
1;