local tickcount = 0;
function tick()
  while true do
    tickcount = tickcount +1;
    print("inner loop " .. tickcount);
  end
end