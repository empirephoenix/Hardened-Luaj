local tickcount = 0;
  leak = {}

function tick()
  while true do
    tickcount = tickcount +1;
    local string = "inner loop " .. tickcount;
    if tickcount % 100 == 0 then
      leak[tickcount]=string
    end
    if tickcount % 1000 == 0 then
      leak = {}
    end
  end
end